import { useId } from 'react'

/**
 * Small, dependency-free charts for the dashboard, built on SVG/CSS and
 * the app's own color tokens (var(--color-*), from index.css). Bars grow
 * in and lines draw themselves on mount (see .animate-grow-y /
 * .animate-draw-line in index.css; both are disabled under
 * prefers-reduced-motion).
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
      {data.map((d, i) => (
        <div key={d.label} className="group flex-1 flex flex-col items-center justify-end h-full min-w-0">
          <div
            className="text-xs font-semibold text-ink mb-1 whitespace-nowrap animate-fade-in"
            style={{ animationDelay: `${300 + i * 80}ms` }}
          >
            {formatValue(d.value)}
          </div>
          <div
            className="animate-grow-y w-full rounded-t-lg min-h-[2px] transition-[filter] duration-200 group-hover:brightness-95"
            style={{
              height: `${Math.max((d.value / max) * 100, 1)}%`,
              backgroundColor: barColor,
              backgroundImage: 'linear-gradient(to top, rgb(0 0 0 / 0.06), rgb(255 255 255 / 0.28))',
              animationDelay: `${i * 80}ms`,
            }}
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
  const gradientId = useId()
  const padding = { top: 16, right: 12, bottom: 24, left: 28 }
  const width = Math.max(data.length * 56, 280)
  const innerW = width - padding.left - padding.right
  const innerH = height - padding.top - padding.bottom
  const baseY = padding.top + innerH

  const points = data.map((d, i) => {
    const x = padding.left + (data.length === 1 ? innerW / 2 : (i / (data.length - 1)) * innerW)
    const y = padding.top + innerH - (Math.min(Math.max(d.value, 0), yMax) / yMax) * innerH
    return { x, y, d }
  })
  const pathD = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ')
  const areaD =
    points.length > 0
      ? `${pathD} L ${points[points.length - 1].x.toFixed(1)} ${baseY} L ${points[0].x.toFixed(1)} ${baseY} Z`
      : ''

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full" style={{ height }} preserveAspectRatio="none">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" style={{ stopColor: lineColor, stopOpacity: 0.22 }} />
          <stop offset="100%" style={{ stopColor: lineColor, stopOpacity: 0 }} />
        </linearGradient>
      </defs>

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
              strokeDasharray={pct === 0 ? undefined : '3 4'}
            />
            <text x={0} y={y + 3} fontSize={9} fill="var(--color-slate-light)">
              {Math.round((pct / 100) * yMax)}
            </text>
          </g>
        )
      })}

      {points.length > 1 && (
        <path d={areaD} fill={`url(#${gradientId})`} className="animate-fade-in" style={{ animationDelay: '500ms' }} />
      )}
      <path
        d={pathD}
        pathLength={1}
        fill="none"
        stroke={lineColor}
        strokeWidth={2.5}
        strokeLinejoin="round"
        strokeLinecap="round"
        vectorEffect="non-scaling-stroke"
        className="animate-draw-line"
      />
      {points.map((p, i) => (
        <g key={i} className="animate-fade-in" style={{ animationDelay: `${400 + i * 60}ms` }}>
          <circle cx={p.x} cy={p.y} r={4} style={{ fill: 'var(--surface)' }} stroke={lineColor} strokeWidth={2} vectorEffect="non-scaling-stroke" />
          <title>{`${p.d.label}: ${formatValue(p.d.value)}`}</title>
          <text x={p.x} y={height - 6} fontSize={9} fill="var(--color-slate)" textAnchor="middle">
            {p.d.label}
          </text>
        </g>
      ))}
    </svg>
  )
}
