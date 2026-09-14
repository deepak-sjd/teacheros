import { useAuth } from '@/lib/auth'
import { Fees } from '@/pages/teacher/Fees'
import { MyFees } from '@/pages/student/MyFees'

export function FeesRoute() {
  const { role } = useAuth()
  if (role === 'STUDENT') return <MyFees />
  return <Fees />
}
