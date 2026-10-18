import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/lib/auth'
import { TenantBrandProvider } from '@/lib/tenantBrand'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { Landing } from '@/pages/Landing'
import { Login } from '@/pages/auth/Login'
import { RoleHome } from '@/pages/RoleHome'
import { Batches } from '@/pages/teacher/Batches'
import { Students } from '@/pages/teacher/Students'
import { StudentDetail } from '@/pages/teacher/StudentDetail'
import { Attendance } from '@/pages/teacher/Attendance'
import { FeesRoute } from '@/pages/FeesRoute'
import { TimetableRoute } from '@/pages/TimetableRoute'
import { HomeworkRoute } from '@/pages/HomeworkRoute'
import { StudyMaterialRoute } from '@/pages/StudyMaterialRoute'
import { TestsRoute } from '@/pages/TestsRoute'
import { AlertsRoute } from '@/pages/AlertsRoute'
import { ReportsPage } from '@/pages/teacher/Reports'
import { AnalyticsPage } from '@/pages/teacher/Analytics'
import { Settings } from '@/pages/Settings'
import { StudentCheckIn } from '@/pages/student/CheckIn'
import { StudentDoubts } from '@/pages/student/Doubts'
import { StudentMessages } from '@/pages/student/Messages'
import { StudentTakeTest } from '@/pages/student/TakeTest'
import { BatchDetail } from '@/pages/teacher/BatchDetail'
import { LeadsPage } from '@/pages/teacher/Leads'
import { CoursesPage } from '@/pages/teacher/Courses'
import { PublicCourseDetail } from '@/pages/public/PublicCourseDetail'
import { PublicCourses } from '@/pages/public/PublicCourses'
import { AnnouncementsRoute } from '@/pages/AnnouncementsRoute'
import { CourseContent } from '@/pages/teacher/CourseContent'
import { StudentLearn } from '@/pages/student/Learn'
import { StudentLearnCourse } from '@/pages/student/LearnCourse'
import { ExamTermsPage } from '@/pages/teacher/ExamTerms'
import { ReportRemarksPage } from '@/pages/teacher/ReportRemarks'
import { FeeSetup } from '@/pages/finance/FeeSetup'
import { AssignFees } from '@/pages/finance/AssignFees'
import { FeeDiscounts } from '@/pages/finance/FeeDiscounts'
import { FinanceReports } from '@/pages/finance/FinanceReports'
import { LeaveRoute } from '@/pages/LeaveRoute'
import { StudentRecords } from '@/pages/student/Records'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <TenantBrandProvider>
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/app" element={<ProtectedRoute><RoleHome /></ProtectedRoute>} />
            <Route path="/app/batches" element={<ProtectedRoute><Batches /></ProtectedRoute>} />
            <Route path="/app/students" element={<ProtectedRoute><Students /></ProtectedRoute>} />
            <Route path="/app/students/:id" element={<ProtectedRoute><StudentDetail /></ProtectedRoute>} />
            <Route path="/app/attendance" element={<ProtectedRoute><Attendance /></ProtectedRoute>} />
            <Route path="/app/homework" element={<ProtectedRoute><HomeworkRoute /></ProtectedRoute>} />
            <Route path="/app/timetable" element={<ProtectedRoute><TimetableRoute /></ProtectedRoute>} />
            <Route path="/app/study-material" element={<ProtectedRoute><StudyMaterialRoute /></ProtectedRoute>} />
            <Route path="/app/tests" element={<ProtectedRoute><TestsRoute /></ProtectedRoute>} />
            <Route path="/app/fees" element={<ProtectedRoute><FeesRoute /></ProtectedRoute>} />
            <Route path="/app/alerts" element={<ProtectedRoute><AlertsRoute /></ProtectedRoute>} />
            <Route path="/app/reports" element={<ProtectedRoute><ReportsPage /></ProtectedRoute>} />
            <Route path="/app/analytics" element={<ProtectedRoute><AnalyticsPage /></ProtectedRoute>} />
            <Route path="/app/settings" element={<ProtectedRoute><Settings /></ProtectedRoute>} />
            <Route path="/app/checkin" element={<ProtectedRoute><StudentCheckIn /></ProtectedRoute>} />
            <Route path="/app/doubts" element={<ProtectedRoute><StudentDoubts /></ProtectedRoute>} />
            <Route path="/app/messages" element={<ProtectedRoute><StudentMessages /></ProtectedRoute>} />
            <Route path="/app/take-test" element={<ProtectedRoute><StudentTakeTest /></ProtectedRoute>} />
            <Route path="/app/batches/:id" element={<ProtectedRoute><BatchDetail /></ProtectedRoute>} />
            <Route path="/app/leads" element={<ProtectedRoute><LeadsPage /></ProtectedRoute>} />
            <Route path="/courses" element={<PublicCourses />} />
            <Route path="/courses/:id" element={<PublicCourseDetail />} />
            <Route path="/app/courses" element={<ProtectedRoute><CoursesPage /></ProtectedRoute>} />
            <Route path="/app/courses/:id/content" element={<ProtectedRoute><CourseContent /></ProtectedRoute>} />
            <Route path="/app/learn" element={<ProtectedRoute><StudentLearn /></ProtectedRoute>} />
            <Route path="/app/learn/:courseId" element={<ProtectedRoute><StudentLearnCourse /></ProtectedRoute>} />
            <Route path="/app/announcements" element={<ProtectedRoute><AnnouncementsRoute /></ProtectedRoute>} />
            <Route path="/app/exam-terms" element={<ProtectedRoute><ExamTermsPage /></ProtectedRoute>} />
            <Route path="/app/report-remarks" element={<ProtectedRoute><ReportRemarksPage /></ProtectedRoute>} />
            <Route path="/app/fee-setup" element={<ProtectedRoute><FeeSetup /></ProtectedRoute>} />
            <Route path="/app/fee-assign" element={<ProtectedRoute><AssignFees /></ProtectedRoute>} />
            <Route path="/app/fee-discounts" element={<ProtectedRoute><FeeDiscounts /></ProtectedRoute>} />
            <Route path="/app/finance-reports" element={<ProtectedRoute><FinanceReports /></ProtectedRoute>} />
            <Route path="/app/leave" element={<ProtectedRoute><LeaveRoute /></ProtectedRoute>} />
            <Route path="/app/records" element={<ProtectedRoute><StudentRecords /></ProtectedRoute>} />
            <Route path="*" element={<Navigate to="/app" replace />} />
          </Routes>
        </TenantBrandProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
