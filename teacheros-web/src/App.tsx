import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/lib/auth'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { Login } from '@/pages/auth/Login'
import { RoleHome } from '@/pages/RoleHome'
import { Batches } from '@/pages/teacher/Batches'
import { Students } from '@/pages/teacher/Students'
import { StudentDetail } from '@/pages/teacher/StudentDetail'
import { Attendance } from '@/pages/teacher/Attendance'
import { HomeworkPage } from '@/pages/teacher/Homework'
import { FeesRoute } from '@/pages/FeesRoute'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<Navigate to="/app" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/app" element={<ProtectedRoute><RoleHome /></ProtectedRoute>} />
          <Route path="/app/batches" element={<ProtectedRoute><Batches /></ProtectedRoute>} />
          <Route path="/app/students" element={<ProtectedRoute><Students /></ProtectedRoute>} />
          <Route path="/app/students/:id" element={<ProtectedRoute><StudentDetail /></ProtectedRoute>} />
          <Route path="/app/attendance" element={<ProtectedRoute><Attendance /></ProtectedRoute>} />
          <Route path="/app/homework" element={<ProtectedRoute><HomeworkPage /></ProtectedRoute>} />
          <Route path="/app/fees" element={<ProtectedRoute><FeesRoute /></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/app" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
