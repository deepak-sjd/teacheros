import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import type { Batch, Page, Student } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, LedgerSkeleton, Modal, Row, Select } from '@/components/ui'
import { Pagination, PAGE_SIZE } from '@/components/Pagination'

export function Students() {
  const [data, setData] = useState<Page<Student> | null>(null)
  const [page, setPage] = useState(0)
  const [batches, setBatches] = useState<Batch[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Student | null>(null)

  function load(p = page) {
    setLoading(true)
    setError(null)
    api
      .get<Page<Student>>('/api/v1/students', { page: p, size: PAGE_SIZE })
      .then((res) => {
        // Landed past the end after a deactivate - step back one page.
        if (res.content.length === 0 && p > 0) {
          setPage(p - 1)
          return
        }
        setData(res)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load students.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load(page)
  }, [page])

  // Batch names for the table and the form's dropdown - loaded once, not per page.
  useEffect(() => {
    api.get<Page<Batch>>('/api/v1/batches', { size: 100 }).then((res) => setBatches(res.content))
  }, [])

  const batchName = (id: string | null) => batches.find((b) => b.id === id)?.name ?? '\u2014'

  async function handleDeactivate(s: Student) {
    if (
      !confirm(
        `Deactivate ${s.fullName}? They'll disappear from lists and rosters. Attendance, marks and fee history are kept.`,
      )
    ) {
      return
    }
    setError(null)
    try {
      await api.del(`/api/v1/students/${s.id}`)
      load(page)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not deactivate this student.')
    }
  }

  return (
    <Layout>
      <PageHeader
        title="Students"
        description="Everyone enrolled across your batches."
        action={<Button onClick={() => setModalOpen(true)}>Add student</Button>}
      />

      {error && <ErrorBanner message={error} />}

      {loading && !data ? (
        <LedgerSkeleton columns={5} />
      ) : !data || data.totalElements === 0 ? (
        <EmptyState
          title="No students yet"
          description="Add your first student to start tracking attendance and fees."
          action={<Button onClick={() => setModalOpen(true)}>Add student</Button>}
        />
      ) : (
        <>
          <Ledger columns={['Name', 'Grade', 'Batch', 'Guardian phone', '']}>
            {data.content.map((s) => (
              <Row key={s.id}>
                <Cell className="font-medium">{s.fullName}</Cell>
                <Cell>{s.grade || '\u2014'}</Cell>
                <Cell>{batchName(s.batchId)}</Cell>
                <Cell>{s.guardianPhone}</Cell>
                <Cell>
                  <div className="flex items-center gap-3 whitespace-nowrap">
                    <Link to={`/app/students/${s.id}`} className="text-sm text-slate hover:text-ink underline underline-offset-2">
                      View
                    </Link>
                    <button
                      onClick={() => setEditing(s)}
                      className="text-sm text-slate hover:text-ink underline underline-offset-2"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => handleDeactivate(s)}
                      className="text-sm text-danger hover:opacity-80 underline underline-offset-2"
                    >
                      Deactivate
                    </button>
                  </div>
                </Cell>
              </Row>
            ))}
          </Ledger>
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            size={data.size}
            onChange={setPage}
          />
        </>
      )}

      <StudentFormModal
        open={modalOpen}
        batches={batches}
        onClose={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false)
          load(page)
        }}
      />

      <StudentFormModal
        open={editing !== null}
        student={editing}
        batches={batches}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null)
          load(page)
        }}
      />
    </Layout>
  )
}

function StudentFormModal({
  open,
  student,
  batches,
  onClose,
  onSaved,
}: {
  open: boolean
  student?: Student | null
  batches: Batch[]
  onClose: () => void
  onSaved: () => void
}) {
  const isEdit = !!student

  const [fullName, setFullName] = useState('')
  const [grade, setGrade] = useState('')
  const [guardianPhone, setGuardianPhone] = useState('')
  const [studentPhone, setStudentPhone] = useState('')
  const [guardianEmail, setGuardianEmail] = useState('')
  const [batchId, setBatchId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // Re-sync form fields whenever a different student is opened for editing
  // (or the "add" modal opens fresh) - this modal instance is reused rather
  // than remounted, so state won't reset on its own.
  useEffect(() => {
    if (!open) return
    setFullName(student?.fullName ?? '')
    setGrade(student?.grade ?? '')
    setGuardianPhone(student?.guardianPhone ?? '')
    setStudentPhone(student?.phoneNumber ?? '')
    setGuardianEmail(student?.guardianEmail ?? '')
    setBatchId(student?.batchId ?? '')
    setError(null)
  }, [open, student])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      const body = {
        fullName,
        grade: grade || undefined,
        guardianPhone,
        phoneNumber: studentPhone || undefined,
        guardianEmail: guardianEmail || undefined,
        batchId: batchId || undefined,
      }
      if (isEdit && student) {
        await api.patch(`/api/v1/students/${student.id}`, body)
      } else {
        await api.post('/api/v1/students', body)
      }
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : `Could not ${isEdit ? 'update' : 'add'} the student.`)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={isEdit ? 'Edit student' : 'Add student'}>
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} required />
        <Input label="Grade / class" value={grade} onChange={(e) => setGrade(e.target.value)} placeholder="e.g. 10" />
        <Input
          label="Guardian phone"
          value={guardianPhone}
          onChange={(e) => setGuardianPhone(e.target.value)}
          placeholder="+91 98765 43210"
          required
        />
        <Input
          label="Student's own phone (optional)"
          value={studentPhone}
          onChange={(e) => setStudentPhone(e.target.value)}
          placeholder="Only if the student should log in themselves"
        />
        <Input
          label="Guardian email (optional)"
          type="email"
          value={guardianEmail}
          onChange={(e) => setGuardianEmail(e.target.value)}
          placeholder="For email alerts, in addition to SMS"
        />
        <Select label="Batch" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
          <option value="">No batch yet</option>
          {batches.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </Select>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? (isEdit ? 'Saving\u2026' : 'Adding\u2026') : isEdit ? 'Save changes' : 'Add student'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
