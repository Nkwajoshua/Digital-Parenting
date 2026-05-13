import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { approveTimeRequest, denyTimeRequest, listenPendingTimeRequests, listenRecentUsageSessions, sendBlockAppCommand, sendSetLimitCommand, sendUnblockAppCommand } from '../services/dashboardApi'

export default function ChildDetailPage() {
  const { childUid } = useParams()
  const [requests, setRequests] = useState([])
  const [sessions, setSessions] = useState([])
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const unsubReq = listenPendingTimeRequests((rows) => setRequests(rows.filter((r) => r.childUid === childUid)), console.error)
    const unsubSessions = listenRecentUsageSessions(childUid, setSessions, console.error)
    return () => { unsubReq(); unsubSessions() }
  }, [childUid])

  const run = async (fn) => {
    try { setBusy(true); await fn() } catch (e) { console.error(e); alert('Action failed') } finally { setBusy(false) }
  }

  return <div>
    <h1>Child Detail: {childUid}</h1>
    <div className="card">
      <h3>Quick Controls</h3>
      <button disabled={busy} onClick={() => run(() => sendBlockAppCommand(childUid, 'com.instagram.android', 'Instagram'))}>Block Instagram</button>
      <button disabled={busy} onClick={() => run(() => sendUnblockAppCommand(childUid, 'com.instagram.android', 'Instagram'))}>Unblock Instagram</button>
      <button disabled={busy} onClick={() => run(() => sendSetLimitCommand(childUid, 'com.instagram.android', 'Instagram', 30))}>Set Instagram Limit 30m</button>
    </div>
    <div className="card"><h3>Pending Time Requests</h3>
      {requests.length === 0 ? <p>No pending requests.</p> : requests.map((req) => <div key={req.id} className="row"><p>{req.deviceName} • {req.appName} • {req.requestedMinutes}m</p>
        <button onClick={() => run(() => approveTimeRequest(req.id, req.requestedMinutes))}>Approve</button>
        <button className="danger" onClick={() => run(() => denyTimeRequest(req.id))}>Deny</button></div>)}
    </div>
    <div className="card"><h3>Recent Usage (latest 10)</h3>
      {sessions.length === 0 ? <p>No usage sessions.</p> : sessions.map((s) => <div className="row" key={s.id}><span>{s.appName || 'Unknown App'} ({s.appPackage})</span><span>{s.duration || 0} sec</span><span>{s.startTime?.toDate?.()?.toLocaleString?.() || '-'} → {s.endTime?.toDate?.()?.toLocaleString?.() || '-'}</span></div>)}
    </div>
  </div>
}
