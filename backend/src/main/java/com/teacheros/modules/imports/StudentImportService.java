package com.teacheros.modules.imports;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.imports.TabularFileReader.ParsedFile;
import com.teacheros.modules.imports.TabularFileReader.RawRow;
import com.teacheros.modules.imports.dto.ImportDtos.ImportResult;
import com.teacheros.modules.imports.dto.ImportDtos.RowError;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Bulk student import from a .csv/.xlsx file. Same validation rules as
 * StudentDtos.CreateStudentRequest, plus the checks a single-record create
 * gets for free from the database but a 500-row file needs reported per row
 * (duplicate phone numbers, unknown batch names).
 *
 * Tenant scoping: the tenant comes only from CurrentTenantProvider and
 * batchName is resolved only against that tenant's own batches, so a file
 * can never attach a student to another tenant's batch or create data in
 * another tenant.
 */
@Service
public class StudentImportService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String EXPECTED_COLUMNS = "fullName, grade, guardianPhone, phoneNumber, guardianEmail, batchName";

    // Header text is lower-cased with everything but letters/digits removed
    // before lookup, so "Full Name", "full_name" and "FULLNAME" all match.
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("fullname", "fullName"),
            Map.entry("name", "fullName"),
            Map.entry("studentname", "fullName"),
            Map.entry("grade", "grade"),
            Map.entry("class", "grade"),
            Map.entry("guardianphone", "guardianPhone"),
            Map.entry("guardianmobile", "guardianPhone"),
            Map.entry("parentphone", "guardianPhone"),
            Map.entry("parentmobile", "guardianPhone"),
            Map.entry("phonenumber", "phoneNumber"),
            Map.entry("studentphone", "phoneNumber"),
            Map.entry("studentphonenumber", "phoneNumber"),
            Map.entry("guardianemail", "guardianEmail"),
            Map.entry("parentemail", "guardianEmail"),
            Map.entry("batch", "batchName"),
            Map.entry("batchname", "batchName"));

    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final int maxRows;

    public StudentImportService(
            StudentRepository studentRepository,
            BatchRepository batchRepository,
            CurrentTenantProvider currentTenantProvider,
            @Value("${app.imports.max-rows:2000}") int maxRows) {
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.maxRows = maxRows;
    }

    @Transactional
    public ImportResult importStudents(MultipartFile file, boolean dryRun, boolean skipInvalidRows) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Choose a file to import.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    "That file is too large. Maximum size is " + (MAX_FILE_BYTES / (1024 * 1024)) + "MB.");
        }

        ParsedFile parsed = TabularFileReader.read(file, maxRows);
        Map<String, Integer> cols = mapColumns(parsed.headers());
        if (parsed.rows().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_NO_ROWS",
                    "The file has a header but no student rows.");
        }

        // Everything below is looked up once, up front - not once per row.
        Map<String, List<Batch>> batchesByName = new HashMap<>();
        for (Batch b : batchRepository.findAllByTenantIdAndActiveTrue(tenantId)) {
            batchesByName.computeIfAbsent(normalizeName(b.getName()), k -> new ArrayList<>()).add(b);
        }
        // All phone numbers, including inactive students' - the unique index
        // uq_students_tenant_phone covers every row, not just active ones.
        Set<String> dbPhones = new HashSet<>(studentRepository.findAllPhoneNumbersByTenantId(tenantId));
        Set<String> dbStudentKeys = new HashSet<>();
        for (Student s : studentRepository.findAllByTenantIdAndActiveTrue(tenantId)) {
            dbStudentKeys.add(studentKey(s.getFullName(), s.getGuardianPhone()));
        }

        Map<String, Integer> filePhoneLine = new HashMap<>();
        Map<String, Integer> fileKeyLine = new HashMap<>();
        List<Student> valid = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        for (RawRow row : parsed.rows()) {
            List<String> messages = new ArrayList<>();

            String fullName = cell(row, cols, "fullName");
            String grade = cell(row, cols, "grade");
            String guardianPhone = normalizePhone(cell(row, cols, "guardianPhone"));
            String studentPhone = normalizePhone(cell(row, cols, "phoneNumber"));
            String guardianEmail = cell(row, cols, "guardianEmail");
            String batchName = cell(row, cols, "batchName");

            if (fullName.isEmpty()) {
                messages.add("Full name is required.");
            } else if (fullName.length() > 255) {
                messages.add("Full name is too long (max 255 characters).");
            }
            if (grade.length() > 50) {
                messages.add("Grade is too long (max 50 characters).");
            }

            boolean guardianPhoneValid = false;
            if (guardianPhone.isEmpty()) {
                messages.add("Guardian phone is required.");
            } else if (!PHONE.matcher(guardianPhone).matches()) {
                messages.add("Guardian phone must be 10-15 digits, optionally starting with +.");
            } else {
                guardianPhoneValid = true;
            }

            if (!studentPhone.isEmpty()) {
                if (!PHONE.matcher(studentPhone).matches()) {
                    messages.add("Student phone must be 10-15 digits, optionally starting with +.");
                } else if (dbPhones.contains(studentPhone)) {
                    messages.add("Student phone " + studentPhone + " is already used by another student.");
                } else if (filePhoneLine.containsKey(studentPhone)) {
                    messages.add("Student phone " + studentPhone + " is already used on line "
                            + filePhoneLine.get(studentPhone) + ".");
                }
            }

            if (!guardianEmail.isEmpty() && (guardianEmail.length() > 255 || !EMAIL.matcher(guardianEmail).matches())) {
                messages.add("Guardian email is not a valid email address.");
            }

            UUID batchId = null;
            if (!batchName.isEmpty()) {
                List<Batch> matches = batchesByName.getOrDefault(normalizeName(batchName), List.of());
                if (matches.isEmpty()) {
                    messages.add(
                            "Batch \"" + batchName + "\" was not found. Check the spelling or create the batch first.");
                } else if (matches.size() > 1) {
                    messages.add("More than one batch is named \"" + batchName + "\". Rename one so names are unique.");
                } else {
                    batchId = matches.get(0).getId();
                }
            }

            String key = null;
            if (!fullName.isEmpty() && guardianPhoneValid) {
                key = studentKey(fullName, guardianPhone);
                if (dbStudentKeys.contains(key)) {
                    messages.add("A student named \"" + fullName + "\" with this guardian phone already exists.");
                } else if (fileKeyLine.containsKey(key)) {
                    messages.add("Duplicate of line " + fileKeyLine.get(key) + " (same name and guardian phone).");
                }
            }

            if (messages.isEmpty()) {
                Student student = new Student(tenantId, batchId, fullName, grade.isEmpty() ? null : grade,
                        guardianPhone);
                student.setPhoneNumber(studentPhone.isEmpty() ? null : studentPhone);
                student.setGuardianEmail(guardianEmail.isEmpty() ? null : guardianEmail);
                valid.add(student);
                // Only valid rows are remembered, so one bad row doesn't make
                // every later row that resembles it look like a duplicate.
                if (!studentPhone.isEmpty()) {
                    filePhoneLine.put(studentPhone, row.lineNumber());
                }
                fileKeyLine.put(key, row.lineNumber());
            } else {
                errors.add(new RowError(row.lineNumber(), messages));
            }
        }

        int imported = 0;
        boolean blockedByErrors = !errors.isEmpty() && !skipInvalidRows;
        if (!dryRun && !blockedByErrors && !valid.isEmpty()) {
            try {
                // Flushed here so a unique-index clash with a concurrent
                // request surfaces inside this try, not at commit.
                studentRepository.saveAllAndFlush(valid);
            } catch (DataIntegrityViolationException e) {
                throw new ApiException(HttpStatus.CONFLICT, "IMPORT_CONFLICT",
                        "Someone else changed the student list while this file was importing. Nothing was imported - please try again.");
            }
            imported = valid.size();
        }

        return new ImportResult(dryRun, parsed.rows().size(), valid.size(), imported, errors.size(), errors);
    }

    private Map<String, Integer> mapColumns(List<String> headers) {
        Map<String, Integer> cols = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String canonical = HEADER_ALIASES.get(headers.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
            if (canonical != null) {
                cols.putIfAbsent(canonical, i);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String required : List.of("fullName", "guardianPhone")) {
            if (!cols.containsKey(required)) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_COLUMNS",
                    "The file is missing required column(s): " + String.join(", ", missing)
                            + ". Expected columns: " + EXPECTED_COLUMNS + ".");
        }
        return cols;
    }

    private static String cell(RawRow row, Map<String, Integer> cols, String key) {
        Integer idx = cols.get(key);
        if (idx == null || idx >= row.cells().size()) {
            return "";
        }
        String value = row.cells().get(idx);
        return value == null ? "" : value.trim();
    }

    private static String normalizePhone(String raw) {
        return raw.replaceAll("[\\s\\-().]", "");
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String studentKey(String fullName, String guardianPhone) {
        return normalizeName(fullName) + "|" + guardianPhone;
    }
}
