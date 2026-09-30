/**
 * Small, dependency-free charts for the dashboard. Deliberately not built
 * on a charting library - these two shapes (a day-by-day trend line and a
 * per-category bar chart) are all the app currently needs, and a hand-
 * rolled SVG/CSS version keeps the bundle free of a new dependency and
 * matches the app's own color tokens (var(--color-*), from index.css)
 * exactly rather than a library's default palette.
 */

export interface ChartDatum {
  label: string
  value: number
}

export function BarChart({
  data,
  maxValue,
  formatValue = (v) => `${Math.round(v)}`,
  barColor = 'var(--color-ink)',
  height = 200,
}: {
  data: ChartDatum[]
  maxValue?: number
  formatValue?: (value: number) => string
  barColor?: string
  height?: number
}) {
  const max = maxValue ?? Math.max(...data.map((d) => d.value), 1)

  return (
    <div className="flex items-end gap-3 sm:gap-4" style={{ height }}>
      {data.map((d) => (
        <div key={d.label} className="flex-1 flex flex-col items-center justify-end h-full min-w-0">
          <div className="text-xs font-medium text-ink mb-1 whitespace-nowrap">{formatValue(d.value)}</div>
          <div
            className="w-full rounded-t-md min-h-[2px] transition-all"
            style={{ height: `${Math.max((d.value / max) * 100, 1)}%`, backgroundColor: barColor }}
            title={`${d.label}: ${formatValue(d.value)}`}
          />
          <div className="text-xs text-slate mt-2 text-center truncate w-full" title={d.label}>
            {d.label}
          </div>
        </div>
      ))}
    </div>
  )
}

export function LineChart({
  data,
  formatValue = (v) => `${Math.round(v)}`,
  yMax = 100,
  height = 200,
  lineColor = 'var(--color-ink)',
}: {
  data: ChartDatum[]
  formatValue?: (value: number) => string
  yMax?: number
  height?: number
  lineColor?: string
}) {
  const padding = { top: 16, right: 12, bottom: 24, left: 28 }
  const width = Math.max(data.length * 56, 280)
  const innerW = width - padding.left - padding.right
  const innerH = height - padding.top - padding.bottom

  const points = data.map((d, i) => {
    const x = padding.left + (data.length === 1 ? innerW / 2 : (i / (data.length - 1)) * innerW)
    const y = padding.top + innerH - (Math.min(Math.max(d.value, 0), yMax) / yMax) * innerH
    return { x, y, d }
  })
  const pathD = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ')

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full" style={{ height }} preserveAspectRatio="none">
      {[0, 50, 100].map((pct) => {
        const y = padding.top + innerH - (pct / 100) * innerH
        return (
          <g key={pct}>
            <line
              x1={padding.left}
              y1={y}
              x2={width - padding.right}
              y2={y}
              stroke="var(--color-line)"
              strokeWidth={1}
            />
            <text x={0} y={y + 3} fontSize={9} fill="var(--color-slate-light)">
              {Math.round((pct / 100) * yMax)}
            </text>
          </g>
        )
      })}
      <path d={pathD} fill="none" stroke={lineColor} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
      {points.map((p, i) => (
        <g key={i}>
          <circle cx={p.x} cy={p.y} r={3.5} fill={lineColor} />
          <title>{`${p.d.label}: ${formatValue(p.d.value)}`}</title>
          <text x={p.x} y={height - 6} fontSize={9} fill="var(--color-slate)" textAnchor="middle">
            {p.d.label}
          </text>
        </g>
      ))}
    </svg>
  )
}
