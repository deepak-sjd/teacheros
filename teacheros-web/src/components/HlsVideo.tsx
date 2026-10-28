import { useEffect, useRef } from 'react'
import Hls from 'hls.js'

/**
 * A <video> that plays HLS everywhere. Safari plays .m3u8 natively; Chrome,
 * Firefox and Edge need hls.js. A non-HLS URL is just set as the source.
 *
 * Resume: `resumeAt` is used on first load; if the source later changes (a
 * fresh signed URL after the old one expired) playback restarts from wherever
 * the viewer had got to, not from `resumeAt` again.
 *
 * A fatal network error usually means the signed URL expired (the CDN refuses
 * every segment after expiry), so it asks the parent for a new one.
 */
export function HlsVideo({
  src,
  resumeAt,
  onTimeUpdate,
  onPause,
  onEnded,
  onSourceExpired,
  className,
}: {
  src: string
  resumeAt: number
  onTimeUpdate?: (seconds: number) => void
  onPause?: (seconds: number) => void
  onEnded?: (seconds: number) => void
  onSourceExpired?: () => void
  className?: string
}) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const lastTime = useRef(0)
  const expiredRef = useRef(onSourceExpired)
  expiredRef.current = onSourceExpired

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    const start = lastTime.current > 0 ? lastTime.current : resumeAt
    let hls: Hls | null = null

    const seekToStart = () => {
      if (start > 0 && Math.abs(video.currentTime - start) > 1) video.currentTime = start
    }
    const isHls = /\.m3u8(\?|$)/.test(src)

    if (!isHls || video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = src
      video.addEventListener('loadedmetadata', seekToStart)
    } else if (Hls.isSupported()) {
      hls = new Hls({ startPosition: start > 0 ? start : -1 })
      hls.loadSource(src)
      hls.attachMedia(video)
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (data.fatal && data.type === Hls.ErrorTypes.NETWORK_ERROR) expiredRef.current?.()
      })
    } else {
      video.src = src
    }

    return () => {
      video.removeEventListener('loadedmetadata', seekToStart)
      hls?.destroy()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src])

  return (
    <video
      ref={videoRef}
      controls
      playsInline
      className={className}
      onTimeUpdate={(e) => {
        lastTime.current = e.currentTarget.currentTime
        onTimeUpdate?.(Math.floor(e.currentTarget.currentTime))
      }}
      onPause={(e) => onPause?.(Math.floor(e.currentTarget.currentTime))}
      onEnded={(e) => onEnded?.(Math.floor(e.currentTarget.currentTime))}
    />
  )
}
