import { Link, Route, Routes } from 'react-router-dom'
import { useMemo } from 'react'
import DashboardPage from './pages/DashboardPage'
import ChildrenPage from './pages/ChildrenPage'
import ChildDetailPage from './pages/ChildDetailPage'
import DebugPanelPage from './pages/DebugPanelPage'

export default function App() {
  const debugDefaults = useMemo(() => ({
    childUid: null,
    firebaseStatus: 'Connected (client init only)',
    latestCommand: null,
    latestRequest: null,
    latestSync: null,
  }), [])

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <h2>Parent Dashboard</h2>
        <nav>
          <Link to="/">Home</Link>
          <Link to="/children">Child Devices</Link>
          <Link to="/debug">Debug Panel</Link>
        </nav>
      </aside>
      <main className="content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/children" element={<ChildrenPage />} />
          <Route path="/children/:childUid" element={<ChildDetailPage />} />
          <Route path="/debug" element={<DebugPanelPage {...debugDefaults} />} />
        </Routes>
      </main>
    </div>
  )
}
