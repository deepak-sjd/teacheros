package com.teacheros.modules.imports;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.academic.AcademicStudentRepository;
import com.teacheros.modules.academic.AcademicStudentRepository.SectionCount;
import com.teacheros.modules.academic.AcademicYear;
import com.teacheros.modules.academic.AcademicYearRepository;
import com.teacheros.modules.academic.SchoolClass;
import com.teacheros.modules.academic.SchoolClassRepository;
import com.teacheros.modules.academic.Section;
import com.teacheros.modules.academic.SectionRepository;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.imports.TabularFileReader.ParsedFile;
import com.teacheros.modules.imports.TabularFileReader.RawRow;
import com.teacheros.modules.imports.dto.ImportDtos.ImportResult;
import com.teacheros.modules.imports.dto.ImportDtos.RowError;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.tenant.TenantConfigService;
import com.teacheros.modules.tenant.TenantType;
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
 * SCHOOL tenants can also place students: className, sectionName and
 * rollNumber columns put each student in a section of the CURRENT academic
 * year, with the same rules as StudentPlacementService (section exists and
 * is active, roll number unique per section and year, capacity respected).
 * COACHING tenants are unaffected: those columns are not even recognised and
 * "Class" still means grade.
 *
 * Tenant scoping: the tenant comes only from CurrentTenantProvider and every
 * lookup (batch, class, section, year) is against that tenant's own rows, so
 * a file can never attach a student to another tenant's data.
 */
@Service
public class StudentImportService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // Same rule as StudentPlacementService (roll numbers are upper-cased first).
    private static final Pattern ROLL = Pattern.compile("^[A-Z0-9-]{1,20}$");
    private static final String EXPECTED_COLUMNS = "fullName, grade, guardianPhone, phoneNumber, guardianEmail, batchName";
    private static final String EXPECTED_SCHOOL_COLUMNS = EXPECTED_COLUMNS + ", className, sectionName, rollNumber";

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

    // Checked BEFORE HEADER_ALIASES, and only for SCHOOL tenants - this is why
    // "Class" means the class name for a school but still means grade for coaching.
    private static final Map<String, String> SCHOOL_HEADER_ALIASES = Map.ofEntries(
            Map.entry("class", "className"),
            Map.entry("classname", "className"),
            Map.entry("section", "sectionName"),
            Map.entry("sectionname", "sectionName"),
            Map.entry("rollnumber", "rollNumber"),
            Map.entry("rollno", "rollNumber"),
            Map.entry("roll", "rollNumber"));

    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final AcademicYearRepository yearRepository;
    private final TenantConfigService tenantConfigService;
    private final CurrentTenantProvider currentTenantProvider;
    private final int maxRows;

    public StudentImportService(
            StudentRepository studentRepository,
            BatchRepository batchRepository,
            AcademicStudentRepository academicStudentRepository,
            SchoolClassRepository classRepository,
            SectionRepository sectionRepository,
            AcademicYearRepository yearRepository,
            TenantConfigService tenantConfigService,
            CurrentTenantProvider currentTenantProvider,
            @Value("${app.imports.max-rows:2000}") int maxRows) {
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.yearRepository = yearRepository;
        this.tenantConfigService = tenantConfigService;
        this.currentTenantProvider = currentTenantProvider;
        this.maxRows = maxRows;
    }

    /**
     * What a SCHOOL import needs to know, loaded once up front rather than per row.
     */
    private static final class SchoolLookup {
        final Map<String, List<SchoolClass>> classesByKey = new HashMap<>();
        final Map<UUID, Map<String, Section>> sectionsByClass = new HashMap<>();
        final Map<UUID, Long> enrolled = new HashMap<>(); // database count + valid rows so far
        final Set<String> takenRolls = new HashSet<>(); // sectionId|roll already in the database
        final Map<String, Integer> fileRollLine = new HashMap<>(); // sectionId|roll -> line, valid rows only
        AcademicYear currentYear;
    }

    private record Placement(Section section, String roll) {
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

        boolean school = tenantConfigService.get().tenantType() == TenantType.SCHOOL;

        ParsedFile parsed = TabularFileReader.read(file, maxRows);
        Map<String, Integer> cols = mapColumns(parsed.headers(), school);
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
        SchoolLookup lookup = school ? loadSchoolLookup(tenantId) : null;

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

            Placement placement = school ? resolvePlacement(row, cols, lookup, messages) : null;

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
                if (placement != null) {
                    student.setSectionId(placement.section().getId());
                    student.setAcademicYearId(lookup.currentYear.getId());
                    student.setRollNumber(placement.roll());
                    // Reserve the seat and roll number so later rows see them as taken.
                    lookup.enrolled.merge(placement.section().getId(), 1L, Long::sum);
                    if (placement.roll() != null) {
                        lookup.fileRollLine.put(rollKey(placement.section(), placement.roll()), row.lineNumber());
                    }
                }
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

    private SchoolLookup loadSchoolLookup(UUID tenantId) {
        SchoolLookup l = new SchoolLookup();
        for (SchoolClass c : classRepository.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantId)) {
            if (c.isActive()) {
                l.classesByKey.computeIfAbsent(classKey(c.getName()), k -> new ArrayList<>()).add(c);
            }
        }
        List<Section> sections = sectionRepository.findByTenantId(tenantId);
        for (Section s : sections) {
            if (s.isActive()) {
                l.sectionsByClass.computeIfAbsent(s.getClassId(), k -> new HashMap<>()).put(sectionKey(s.getName()), s);
            }
        }
        for (SectionCount c : academicStudentRepository.countActiveBySection(tenantId)) {
            l.enrolled.put(c.getSectionId(), c.getStudentCount());
        }
        l.currentYear = yearRepository.findByTenantIdAndCurrentTrue(tenantId).orElse(null);
        if (l.currentYear != null && !sections.isEmpty()) {
            for (Student s : academicStudentRepository.findByTenantIdAndAcademicYearIdAndSectionIdInAndActiveTrue(
                    tenantId, l.currentYear.getId(), sections.stream().map(Section::getId).toList())) {
                if (s.getRollNumber() != null) {
                    l.takenRolls.add(s.getSectionId() + "|" + s.getRollNumber());
                }
            }
        }
        return l;
    }

    /**
     * Adds a message for every problem with this row's class/section/roll and
     * returns the placement only when it is usable. Returns null both for "no
     * placement asked for" and for "placement invalid" - the caller tells them
     * apart by whether messages grew.
     */
    private Placement resolvePlacement(RawRow row, Map<String, Integer> cols, SchoolLookup l, List<String> messages) {
        String className = cell(row, cols, "className");
        String sectionName = cell(row, cols, "sectionName");
        String rollRaw = cell(row, cols, "rollNumber");

        if (className.isEmpty() && sectionName.isEmpty()) {
            if (!rollRaw.isEmpty()) {
                messages.add("Roll number needs a class and section.");
            }
            return null; // no placement requested - the student is created unplaced
        }
        if (className.isEmpty()) {
            messages.add("Class is required when a section is given.");
            return null;
        }
        if (sectionName.isEmpty()) {
            messages.add("Section is required when a class is given.");
            return null;
        }
        if (l.currentYear == null) {
            // File-level, not per row: it would be the same message on every row.
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_CURRENT_YEAR",
                    "Set a current academic year before importing students into classes.");
        }

        List<SchoolClass> matches = l.classesByKey.getOrDefault(classKey(className), List.of());
        if (matches.isEmpty()) {
            messages.add("Class \"" + className + "\" was not found. Check the spelling or create the class first.");
            return null;
        }
        if (matches.size() > 1) {
            messages.add("More than one class matches \"" + className + "\". Use the full class name.");
            return null;
        }
        SchoolClass schoolClass = matches.get(0);
        Section section = l.sectionsByClass.getOrDefault(schoolClass.getId(), Map.of()).get(sectionKey(sectionName));
        if (section == null) {
            messages.add("Class \"" + schoolClass.getName() + "\" has no section \"" + sectionName + "\".");
            return null;
        }

        String label = schoolClass.getName() + " " + section.getName();
        String roll = null;
        boolean ok = true;
        if (!rollRaw.isEmpty()) {
            roll = rollRaw.toUpperCase(Locale.ROOT);
            String key = rollKey(section, roll);
            if (!ROLL.matcher(roll).matches()) {
                messages.add("Roll number can use letters, digits and dashes only (max 20 characters).");
                ok = false;
            } else if (l.takenRolls.contains(key)) {
                messages.add("Roll number " + roll + " is already taken in " + label + ".");
                ok = false;
            } else if (l.fileRollLine.containsKey(key)) {
                messages.add("Roll number " + roll + " is already used on line " + l.fileRollLine.get(key)
                        + " for " + label + ".");
                ok = false;
            }
        }
        if (section.getCapacity() != null && l.enrolled.getOrDefault(section.getId(), 0L) >= section.getCapacity()) {
            messages.add(label + " is full (capacity " + section.getCapacity() + ").");
            ok = false;
        }
        return ok ? new Placement(section, roll) : null;
    }

    private Map<String, Integer> mapColumns(List<String> headers, boolean school) {
        Map<String, Integer> cols = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalized = headers.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            String canonical = school && SCHOOL_HEADER_ALIASES.containsKey(normalized)
                    ? SCHOOL_HEADER_ALIASES.get(normalized)
                    : HEADER_ALIASES.get(normalized);
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
                            + ". Expected columns: " + (school ? EXPECTED_SCHOOL_COLUMNS : EXPECTED_COLUMNS) + ".");
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

    // "Class 8", "class8", "Grade 8" and "8" all compare equal.
    private static String classKey(String name) {
        String k = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (String prefix : List.of("standard", "class", "grade", "std")) {
            if (k.startsWith(prefix) && k.length() > prefix.length()) {
                return k.substring(prefix.length());
            }
        }
        return k;
    }

    private static String sectionKey(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String rollKey(Section section, String roll) {
        return section.getId() + "|" + roll;
    }
}
