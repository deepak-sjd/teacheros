import { useEffect, useRef, useState } from 'react'

/**
 * Adds .is-visible (see index.css's .reveal rules) the first time the
 * element scrolls into view, then stops watching - a scroll-entrance
 * animation should play once per visit, not re-trigger every time someone
 * scrolls back past a section.
 */
export function useReveal<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true)
          observer.disconnect()
        }
      },
      { threshold: 0.15 },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  return { ref, visible }
}
