import { type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useReveal } from '@/lib/useReveal'

function Reveal({ children, className = '' }: { children: ReactNode; className?: string }) {
  const { ref, visible } = useReveal<HTMLDivElement>()
  return (
    <div ref={ref} className={`reveal ${visible ? 'is-visible' : ''} ${className}`}>
      {children}
    </div>
  )
}

const FEATURES = [
  {
    title: 'Attendance, two ways',
    body: 'Mark a whole batch by hand in seconds, or let students self check-in with a QR code you project in class.',
  },
  {
    title: 'Fees, collected online',
    body: 'Track dues, send reminders automatically, and let parents pay by card or UPI without a single follow-up call.',
  },
  {
    title: 'Tests, marks, and rank',
    body: 'Run graded tests or quick online MCQ quizzes. Students see their score and batch rank the moment it\u2019s published.',
  },
  {
    title: 'An AI doubt solver',
    body: 'Students ask a question any time and get a clear, patient answer - without waiting for the next class.',
  },
  {
    title: 'One app, every role',
    body: 'Teachers, staff, parents, and students each get a workspace built for them, not a cut-down version of yours.',
  },
  {
    title: 'Alerts that reach parents',
    body: 'Low attendance and overdue fees trigger a message home on their own - nothing to remember to send.',
  },
]

const CAPABILITIES = [
  { value: '6', label: 'Roles, one app' },
  { value: 'QR', label: 'Self check-in' },
  { value: '24/7', label: 'AI doubt solving' },
  { value: 'Live', label: 'Parent alerts' },
]

export function Landing() {
  return (
    <div className="min-h-screen bg-paper text-ink">
      <header className="sticky top-0 z-30 border-b border-line bg-paper/90 backdrop-blur">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <span className="font-display text-xl tracking-tight">TeacherOS</span>
          <Link
            to="/login"
            className="rounded-md bg-ink text-paper px-4 py-2 text-sm font-medium transition-all duration-150 hover:bg-ink-light active:scale-[0.97]"
          >
            Log in
          </Link>
        </div>
      </header>

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div
          aria-hidden
          className="absolute -top-24 right-0 h-80 w-80 rounded-full bg-chalk-soft blur-3xl opacity-70"
        />
        <div className="relative max-w-6xl mx-auto px-4 sm:px-6 pt-16 pb-20 sm:pt-24 sm:pb-28 text-center">
          <Reveal>
            <span className="inline-block rounded-full bg-chalk-soft text-ink/80 text-xs font-medium px-3 py-1 mb-6">
              Built for schools &amp; coaching centers
            </span>
            <h1 className="font-display text-4xl sm:text-5xl md:text-6xl leading-[1.08] max-w-3xl mx-auto">
              Run your batches, attendance, and fees in one place.
            </h1>
            <p className="text-slate text-base sm:text-lg mt-6 max-w-xl mx-auto">
              TeacherOS brings teachers, students, and parents into a single workspace -
              attendance, tests, fees, and alerts, without the spreadsheet juggling.
            </p>
            <div className="mt-9 flex flex-col sm:flex-row gap-3 justify-center">
              <Link
                to="/login"
                className="rounded-md bg-ink text-paper px-6 py-3 text-sm font-medium transition-all duration-150 hover:bg-ink-light active:scale-[0.97]"
              >
                Get started
              </Link>
              <a
                href="#features"
                className="rounded-md bg-white text-ink border border-line px-6 py-3 text-sm font-medium transition-all duration-150 hover:bg-paper-dim active:scale-[0.97]"
              >
                See what it does
              </a>
            </div>
          </Reveal>
        </div>
      </section>

      {/* Features */}
      <section id="features" className="max-w-6xl mx-auto px-4 sm:px-6 py-16 sm:py-20">
        <Reveal className="text-center max-w-xl mx-auto mb-12">
          <h2 className="font-display text-3xl sm:text-4xl">Everything the day-to-day needs</h2>
          <p className="text-slate mt-3">No add-ons to buy later - it's one app from day one.</p>
        </Reveal>
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {FEATURES.map((f, i) => (
            <Reveal key={f.title}>
              <div className="h-full border border-line rounded-lg bg-white p-6 shadow-sm transition-shadow duration-200 hover:shadow-md">
                <div className="h-9 w-9 rounded-md bg-chalk-soft flex items-center justify-center font-display text-ink mb-4">
                  {i + 1}
                </div>
                <h3 className="font-display text-lg text-ink mb-1.5">{f.title}</h3>
                <p className="text-slate text-sm leading-relaxed">{f.body}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* Screenshot placeholder - a stylized mockup built from the app's
          own design tokens, not a real screenshot (there isn't one to use
          here yet). Swap the inner content for an actual <img> once a
          real dashboard screenshot exists. */}
      <section className="max-w-6xl mx-auto px-4 sm:px-6 pb-16 sm:pb-20">
        <Reveal>
          <div className="rounded-xl border border-line bg-ink p-2 sm:p-3 shadow-xl">
            <div className="flex items-center gap-1.5 px-3 py-2">
              <span className="h-2.5 w-2.5 rounded-full bg-danger" />
              <span className="h-2.5 w-2.5 rounded-full bg-warn" />
              <span className="h-2.5 w-2.5 rounded-full bg-success" />
            </div>
            <div className="rounded-lg bg-paper p-4 sm:p-8">
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4 mb-5">
                {['Total students', 'Present today', 'Fees collected', 'Avg. marks'].map((label) => (
                  <div key={label} className="border border-line rounded-lg bg-white px-4 py-3">
                    <div className="text-slate text-xs">{label}</div>
                    <div className="font-display text-xl sm:text-2xl text-ink mt-1 bg-paper-dim rounded w-12 h-6" />
                  </div>
                ))}
              </div>
              <div className="border border-line rounded-lg bg-white overflow-hidden">
                <div className="border-b border-line bg-paper-dim px-4 py-2.5 text-xs text-slate font-medium">
                  Recent activity
                </div>
                {[1, 2, 3].map((row) => (
                  <div key={row} className="flex items-center gap-4 px-4 py-3 border-b border-line last:border-0">
                    <div className="h-8 w-8 rounded-full bg-chalk-soft shrink-0" />
                    <div className="h-3 bg-paper-dim rounded flex-1 max-w-[60%]" />
                    <div className="h-3 bg-paper-dim rounded w-14" />
                  </div>
                ))}
              </div>
            </div>
          </div>
        </Reveal>
      </section>

      {/* Capabilities strip */}
      <section className="border-y border-line bg-ink text-paper">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-12">
          <Reveal className="grid grid-cols-2 sm:grid-cols-4 gap-8 text-center">
            {CAPABILITIES.map((c) => (
              <div key={c.label}>
                <div className="font-display text-3xl sm:text-4xl text-chalk">{c.value}</div>
                <div className="text-paper/70 text-sm mt-1">{c.label}</div>
              </div>
            ))}
          </Reveal>
        </div>
      </section>

      {/* CTA */}
      <section className="max-w-6xl mx-auto px-4 sm:px-6 py-16 sm:py-20 text-center">
        <Reveal>
          <h2 className="font-display text-3xl sm:text-4xl max-w-xl mx-auto">
            Spend less time on admin, more time teaching.
          </h2>
          <p className="text-slate mt-3 max-w-md mx-auto">
            Set up your first batch in a few minutes - no setup call needed.
          </p>
          <Link
            to="/login"
            className="inline-block mt-8 rounded-md bg-ink text-paper px-7 py-3 text-sm font-medium transition-all duration-150 hover:bg-ink-light active:scale-[0.97]"
          >
            Get started free
          </Link>
        </Reveal>
      </section>

      <footer className="border-t border-line">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-10 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div>
            <div className="font-display text-lg text-ink">TeacherOS</div>
            <p className="text-slate-light text-xs mt-1">Run your batches, attendance, and fees in one place.</p>
          </div>
          <div className="flex items-center gap-6 text-sm">
            <Link to="/login" className="text-slate hover:text-ink transition-colors">
              Log in
            </Link>
            <span className="text-slate-light text-xs">&copy; {new Date().getFullYear()} TeacherOS</span>
          </div>
        </div>
      </footer>
    </div>
  )
}
