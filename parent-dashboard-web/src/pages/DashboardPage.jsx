import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listenChildren, listenPendingTimeRequests } from '../services/dashboardApi'

export default function DashboardPage() {
  const [children, setChildren] = useState([])
  const [requests, setRequests] = useState([])

  useEffect(() => {
    const unsubChildren = listenChildren(setChildren, console.error)
    const unsubRequests = listenPendingTimeRequests(setRequests, console.error)
    return () => { unsubChildren(); unsubRequests() }
  }, [])

  const activeCount = useMemo(() => children.filter((c) => c.monitoringActive).length, [children])

  return <div>
    <h1>Dashboard Home</h1>
    <section className="grid">
      <div className="card"><h3>Total Child Devices</h3><p>{children.length}</p></div>
      <div className="card"><h3>Online / Active</h3><p>{activeCount}</p></div>
      <div className="card"><h3>Pending Time Requests</h3><p>{requests.length}</p></div>
      <div className="card"><h3>Recent Activity</h3><p>{children[0]?.deviceName ? `${children[0].deviceName} synced recently` : 'No activity yet'}</p></div>
    </section>
    <div className="card">
      <h3>Quick Links</h3>
      <Link to="/children">Manage child devices</Link><br />
      <Link to="/debug">Open debug panel</Link>
    </div>
  </div>
}
