import { useState, type ChangeEvent } from 'react'
import { Modal, Button, ErrorBanner } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import type { ImportResult } from '@/lib/types'

// Leading BOM so Excel opens it as UTF-8 (names with non-English letters
// otherwise come out garbled). The server strips it when reading.
const TEMPLATE_CSV =
  '\uFEFFfullName,grade,guardianPhone,phoneNumber,guardianEmail,batchName\n' +
  'Asha Rao,10,9876543210,,asha.parent@example.com,Grade 10 - Morning\n'

function downloadTemplate() {
  const blob = new Blob([TEMPLATE_CSV], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'students-template.csv'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export function ImportStudentsModal({
  open,
  onClose,
  onImported,
}: {
  open: boolean
  onClose: () => void
  onImported: () => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [inputKey, setInputKey] = useState(0)
  const [preview, setPreview] = useState<ImportResult | null>(null)
  const [done, setDone] = useState<ImportResult | null>(null)
  const [skipInvalid, setSkipInvalid] = useState(false)
  const [busy, setBusy] = useState<'check' | 'import' | null>(null)
  const [error, setError] = useState<string | null>(null)

  function reset() {
    setFile(null)
    setInputKey((k) => k + 1)
    setPreview(null)
    setDone(null)
    setSkipInvalid(false)
    setError(null)
  }

  function handleClose() {
    reset()
    onClose()
  }

  function handleFile(e: ChangeEvent<HTMLInputElement>) {
    setFile(e.target.files?.[0] ?? null)
    setPreview(null)
    setDone(null)
    setSkipInvalid(false)
    setError(null)
  }

  async function run(dryRun: boolean) {
    if (!file) return
    setError(null)
    setBusy(dryRun ? 'check' : 'import')
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('dryRun', String(dryRun))
      formData.append('skipInvalidRows', String(skipInvalid))
      const res = await api.uploadFile<ImportResult>('/api/v1/imports/students', formData)

      if (dryRun) {
        setPreview(res)
      } else if (res.importedRows > 0) {
        setDone(res)
        setPreview(null)
        onImported()
      } else {
        // The file changed under us between the check and the import - the
        // server imported nothing, so show what it found instead.
        setPreview(res)
        setError('Nothing was imported. Review the issues below.')
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not process that file.')
    } finally {
      setBusy(null)
    }
  }

  const blocked = preview !== null && preview.errorRows > 0 && !skipInvalid

  return (
    <Modal open={open} onClose={handleClose} title="Import students">
      {error && <ErrorBanner message={error} />}

      {done ? (
        <div>
          <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 mb-4 border border-success/20">
            Imported {done.importedRows} student{done.importedRows === 1 ? '' : 's'}.
            {done.errorRows > 0 && ` Skipped ${done.errorRows} row${done.errorRows === 1 ? '' : 's'} with errors.`}
          </div>
          <div className="flex justify-end">
            <Button onClick={handleClose}>Close</Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <p className="text-sm text-slate">
            Upload a .csv or .xlsx file. Required columns: <span className="text-ink">fullName</span>,{' '}
            <span className="text-ink">guardianPhone</span>. Optional: grade, phoneNumber, guardianEmail, batchName
            (must match an existing batch).
          </p>
          <button
            type="button"
            onClick={downloadTemplate}
            className="text-sm text-slate hover:text-ink underline underline-offset-2"
          >
            Download template
          </button>
          <p className="text-xs text-slate-light">
            Tip: format the phone columns as Text in Excel so long numbers aren't shown as 9.88E+09.
          </p>

          <input
            key={inputKey}
            type="file"
            accept=".csv,.xlsx"
            onChange={handleFile}
            className="block w-full text-sm text-slate file:mr-3 file:rounded-md file:border-0 file:bg-paper-dim file:px-3 file:py-2 file:text-sm file:font-medium file:text-ink hover:file:bg-line"
          />

          {preview && (
            <div className="space-y-3">
              <p className="text-sm text-ink">
                {preview.validRows} of {preview.totalRows} row{preview.totalRows === 1 ? '' : 's'} ready to import
                {preview.errorRows > 0 && (
                  <span className="text-danger">
                    {' '}
                    · {preview.errorRows} with errors
                  </span>
                )}
                .
              </p>

              {preview.errorRows > 0 && (
                <ul className="max-h-56 overflow-y-auto border border-line rounded-md divide-y divide-line text-sm">
                  {preview.errors.map((e) => (
                    <li key={e.lineNumber} className="px-3 py-2">
                      <span className="font-medium text-ink">Line {e.lineNumber}:</span>{' '}
                      <span className="text-slate">{e.messages.join(' ')}</span>
                    </li>
                  ))}
                </ul>
              )}

              {preview.errorRows > 0 && preview.validRows > 0 && (
                <label className="flex items-start gap-2 text-sm text-ink cursor-pointer">
                  <input
                    type="checkbox"
                    className="mt-0.5"
                    checked={skipInvalid}
                    onChange={(e) => setSkipInvalid(e.target.checked)}
                  />
                  <span>
                    Skip the {preview.errorRows} row{preview.errorRows === 1 ? '' : 's'} with errors and import the
                    other {preview.validRows}
                  </span>
                </label>
              )}
              {blocked && preview.validRows > 0 && (
                <p className="text-xs text-slate-light">
                  Or fix the file and check it again - nothing is imported while rows have errors.
                </p>
              )}
            </div>
          )}

          <div className="flex gap-2 justify-end pt-2">
            <Button type="button" variant="secondary" onClick={handleClose}>
              Cancel
            </Button>
            <Button
              type="button"
              variant={preview ? 'secondary' : 'primary'}
              onClick={() => run(true)}
              disabled={!file || busy !== null}
            >
              {busy === 'check' ? 'Checking…' : preview ? 'Check again' : 'Check file'}
            </Button>
            {preview && (
              <Button onClick={() => run(false)} disabled={busy !== null || preview.validRows === 0 || blocked}>
                {busy === 'import' ? 'Importing…' : `Import ${preview.validRows} student${preview.validRows === 1 ? '' : 's'}`}
              </Button>
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}
