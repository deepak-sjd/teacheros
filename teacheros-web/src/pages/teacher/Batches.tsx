import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Batch, Page } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, Modal, Row } from '@/components/ui'

export function Batches() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)

  function load() {
    setLoading(true)
    api
      .get<Page<Batch>>('/api/v1/batches', { size: 100 })
      .then((res) => setBatches(res.content))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  return (
    <Layout>
      <PageHeader
        title="Batches"
        description="The class groups students are enrolled in."
        action={<Button onClick={() => setModalOpen(true)}>New batch</Button>}
      />

      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : batches.length === 0 ? (
        <EmptyState
          title="No batches yet"
          description="Create your first batch to start enrolling students."
          action={<Button onClick={() => setModalOpen(true)}>New batch</Button>}
        />
      ) : (
        <Ledger columns={['Name', 'Subject', 'Schedule']}>
          {batches.map((b) => (
            <Row key={b.id}>
              <Cell className="font-medium">{b.name}</Cell>
              <Cell>{b.subject || '\u2014'}</Cell>
              <Cell>{b.scheduleSummary || '\u2014'}</Cell>
            </Row>
          ))}
        </Ledger>
      )}

      <NewBatchModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={() => {
          setModalOpen(false)
          load()
        }}
      />
    </Layout>
  )
}

function NewBatchModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState('')
  const [subject, setSubject] = useState('')
  const [scheduleSummary, setScheduleSummary] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await api.post('/api/v1/batches', { name, subject: subject || undefined, scheduleSummary: scheduleSummary || undefined })
      setName('')
      setSubject('')
      setScheduleSummary('')
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the batch.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New batch">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Batch name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Grade 10 - Morning" required />
        <Input label="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="e.g. Mathematics" />
        <Input
          label="Schedule"
          value={scheduleSummary}
          onChange={(e) => setScheduleSummary(e.target.value)}
          placeholder="e.g. Mon/Wed/Fri, 4\u20135 PM"
        />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Creating\u2026' : 'Create batch'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
