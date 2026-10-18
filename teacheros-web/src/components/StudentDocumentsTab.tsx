import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import type { DownloadUrlResponse, StudentDocument, StudentDocumentType } from '@/lib/types'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, Row, Select, Skeleton } from '@/components/ui'

const DOC_TYPE_LABELS: Record<StudentDocumentType, string> = {
  BIRTH_CERTIFICATE: 'Birth certificate',
  TRANSFER_CERTIFICATE: 'Transfer certificate',
  REPORT_CARD: 'Previous report card',
  ID_PROOF: 'ID proof',
  MEDICAL: 'Medical',
  PHOTO: 'Photo',
  OTHER: 'Other',
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * Documents for one student. Staff can upload and delete; a parent can view
 * their own child's documents. Files are never linked directly - "View" asks
 * the API for a short-lived signed URL each time, so access is re-checked.
 */
export function StudentDocumentsTab({ studentId, canManage }: { studentId: string; canManage: boolean }) {
  const [docs, setDocs] = useState<StudentDocument[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const [docType, setDocType] = useState<StudentDocumentType>('BIRTH_CERTIFICATE')
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [fileInputKey, setFileInputKey] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setDocs(null)
    setError(null)
    api
      .get<StudentDocument[]>(`/api/v1/students/${studentId}/documents`)
      .then((list) => {
        if (!cancelled) setDocs(list)
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : 'Could not load the documents.')
      })
    return () => {
      cancelled = true
    }
  }, [studentId])

  async function handleUpload(e: FormEvent) {
    e.preventDefault()
    if (!file) {
      setUploadError('Choose a file to upload.')
      return
    }
    if (title.trim() === '') {
      setUploadError('Give this document a title.')
      return
    }
    setUploadError(null)
    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('docType', docType)
      formData.append('title', title.trim())
      formData.append('file', file)
      const created = await api.uploadFile<StudentDocument>(`/api/v1/students/${studentId}/documents`, formData)
      setDocs((prev) => [created, ...(prev ?? [])])
      setTitle('')
      setFile(null)
      setFileInputKey((k) => k + 1) // clears the native file input
    } catch (err) {
      setUploadError(err instanceof ApiError ? err.message : 'Could not upload the document.')
    } finally {
      setUploading(false)
    }
  }

  async function handleView(doc: StudentDocument) {
    setBusyId(doc.id)
    setError(null)
    try {
      const { url } = await api.get<DownloadUrlResponse>(`/api/v1/students/${studentId}/documents/${doc.id}/download-url`)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not open the document.')
    } finally {
      setBusyId(null)
    }
  }

  async function handleDelete(doc: StudentDocument) {
    if (!window.confirm(`Delete "${doc.title}"? This cannot be undone.`)) return
    setBusyId(doc.id)
    setError(null)
    try {
      await api.del(`/api/v1/students/${studentId}/documents/${doc.id}`)
      setDocs((prev) => (prev ? prev.filter((d) => d.id !== doc.id) : prev))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete the document.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div>
      {error && <ErrorBanner message={error} />}

      {canManage && (
        <form onSubmit={handleUpload} className="border border-line rounded-lg bg-white p-5 mb-6 max-w-2xl space-y-4">
          <p className="text-sm font-medium text-ink">Upload a document</p>
          {uploadError && <ErrorBanner message={uploadError} />}
          <div className="grid gap-4 sm:grid-cols-2">
            <Select label="Type" value={docType} onChange={(e) => setDocType(e.target.value as StudentDocumentType)}>
              {(Object.keys(DOC_TYPE_LABELS) as StudentDocumentType[]).map((t) => (
                <option key={t} value={t}>
                  {DOC_TYPE_LABELS[t]}
                </option>
              ))}
            </Select>
            <Input label="Title" value={title} maxLength={255} placeholder="e.g. Birth certificate" onChange={(e) => setTitle(e.target.value)} />
          </div>
          <label className="block">
            <span className="block text-sm font-medium text-ink mb-1.5">File</span>
            <input
              key={fileInputKey}
              type="file"
              accept="application/pdf,image/png,image/jpeg,image/webp"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-slate file:mr-3 file:rounded-lg file:border file:border-line file:bg-white file:px-3 file:py-2 file:text-sm file:font-medium file:text-ink"
            />
            <span className="block text-xs text-slate mt-1.5">PDF, PNG, JPEG or WebP.</span>
          </label>
          <Button type="submit" disabled={uploading}>
            {uploading ? 'Uploading…' : 'Upload'}
          </Button>
        </form>
      )}

      {docs === null && !error ? (
        <Skeleton className="h-32 rounded-lg" />
      ) : docs && docs.length === 0 ? (
        <EmptyState
          title="No documents yet"
          description={canManage ? 'Upload a birth certificate, transfer certificate or ID proof above.' : 'The school has not uploaded any documents for this student.'}
        />
      ) : (
        docs && (
          <Ledger columns={['Document', 'Type', 'File', 'Uploaded', '']}>
            {docs.map((d) => (
              <Row key={d.id}>
                <Cell className="font-medium">{d.title}</Cell>
                <Cell>{DOC_TYPE_LABELS[d.docType] ?? d.docType}</Cell>
                <Cell>
                  {d.originalFilename} {'\u00b7'} {formatFileSize(d.fileSizeBytes)}
                </Cell>
                <Cell>{formatDate(d.createdAt)}</Cell>
                <Cell>
                  <div className="flex items-center justify-end gap-3 whitespace-nowrap">
                    <button
                      onClick={() => handleView(d)}
                      disabled={busyId === d.id}
                      className="text-sm text-slate hover:text-ink underline underline-offset-2 disabled:opacity-50"
                    >
                      View
                    </button>
                    {canManage && (
                      <button
                        onClick={() => handleDelete(d)}
                        disabled={busyId === d.id}
                        className="text-sm text-danger hover:underline underline-offset-2 disabled:opacity-50"
                      >
                        Delete
                      </button>
                    )}
                  </div>
                </Cell>
              </Row>
            ))}
          </Ledger>
        )
      )}
    </div>
  )
}
