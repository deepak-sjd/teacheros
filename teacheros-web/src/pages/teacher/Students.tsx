import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import type { Batch, Page, Student } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, Modal, Row, Select } from '@/components/ui'

export function Students() {
  const [students, setStudents] = useState<Student[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)

  function load() {
    setLoading(true)
    Promise.all([
      api.get<Page<Student>>('/api/v1/students', { size: 100 }),
      api.get<Page<Batch>>('/api/v1/batches', { size: 100 }),
    ])
      .then(([s, b]) => {
        setStudents(s.content)
        setBatches(b.content)
      })
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const batchName = (id: string | null) => batches.find((b) => b.id === id)?.name ?? '\u2014'

  return (
    <Layout>
      <PageHeader
        title="Students"
        description="Everyone enrolled across your batches."
        action={<Button onClick={() => setModalOpen(true)}>Add student</Button>}
      />

      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : students.length === 0 ? (
        <EmptyState
          title="No students yet"
          description="Add your first student to start tracking attendance and fees."
          action={<Button onClick={() => setModalOpen(true)}>Add student</Button>}
        />
      ) : (
        <Ledger columns={['Name', 'Grade', 'Batch', 'Guardian phone', '']}>
          {students.map((s) => (
            <Row key={s.id}>
              <Cell className="font-medium">{s.fullName}</Cell>
              <Cell>{s.grade || '\u2014'}</Cell>
              <Cell>{batchName(s.batchId)}</Cell>
              <Cell>{s.guardianPhone}</Cell>
              <Cell>
                <Link to={`/app/students/${s.id}`} className="text-sm text-slate hover:text-ink underline underline-offset-2">
                  View
                </Link>
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}

      <NewStudentModal
        open={modalOpen}
        batches={batches}
        onClose={() => setModalOpen(false)}
        onCreated={() => {
          setModalOpen(false)
          load()
        }}
      />
    </Layout>
  )
}

function NewStudentModal({
  open,
  batches,
  onClose,
  onCreated,
}: {
  open: boolean
  batches: Batch[]
  onClose: () => void
  onCreated: () => void
}) {
  const [fullName, setFullName] = useState('')
  const [grade, setGrade] = useState('')
  const [guardianPhone, setGuardianPhone] = useState('')
  const [studentPhone, setStudentPhone] = useState('')
  const [batchId, setBatchId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await api.post('/api/v1/students', {
        fullName,
        grade: grade || undefined,
        guardianPhone,
        phoneNumber: studentPhone || undefined,
        batchId: batchId || undefined,
      })
      setFullName('')
      setGrade('')
      setGuardianPhone('')
      setStudentPhone('')
      setBatchId('')
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add the student.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Add student">
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
            {saving ? 'Adding\u2026' : 'Add student'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
