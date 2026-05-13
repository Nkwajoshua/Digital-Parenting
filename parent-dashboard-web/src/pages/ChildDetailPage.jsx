import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  approveTimeRequest,
  denyTimeRequest,
  listenPendingTimeRequests,
  listenRecentCommands,
  listenRecentUsageSessions,
  sendBlockAppCommand,
  sendSetLimitCommand,
  sendUnblockAppCommand,
} from '../services/dashboardApi'
import { logParentCommand, logParentRequest } from '../services/logger'

export default function ChildDetailPage() {
  const { childUid } = useParams()
  const [requests, setRequests] = useState([])
  const [sessions, setSessions] = useState([])
  const [commands, setCommands] = useState([])
  const [busy, setBusy] = useState(false)
  const [feedback, setFeedback] = useState('')

  useEffect(() => {
    const unsubReq = listenPendingTimeRequests((rows) => setRequests(rows.filter((r) => r.childUid === childUid)), console.error)
    const unsubSessions = listenRecentUsageSessions(childUid, setSessions, console.error)
    const unsubCommands = listenRecentCommands(childUid, setCommands, console.error)

    return () => { unsubReq(); unsubSessions(); unsubCommands() }
  }, [childUid])

  const latestCommand = commands[0]
  const commandStateText = useMemo(() => {
    if (!latestCommand) return 'No commands yet'
    return latestCommand.status || 'pending'
  }, [latestCommand])

  const runCommand = async (fn, commandLabel) => {
    try {
      setBusy(true)
      setFeedback('Sending command…')
      await fn()
      setFeedback('Command queued')
      logParentCommand('Command queued', { childUid, commandLabel })
    } catch (e) {
      logParentCommand('Command failed', { childUid, commandLabel, error: e })
      setFeedback('Command failed')
    } finally { setBusy(false) }
  }

  const runRequestAction = async (fn, actionLabel) => {
    try {
      setBusy(true)
      await fn()
      setFeedback(`${actionLabel} successful`)
      logParentRequest('Request updated', { actionLabel, childUid })
    } catch (e) {
      logParentRequest('Request update failed', { actionLabel, error: e })
      setFeedback(`${actionLabel} failed`)
    } finally { setBusy(false) }
  }

  return <div>
    <h1>Child Detail: {childUid}</h1>
    {feedback && <p>{feedback}</p>}
    <div className="card">
      <h3>Quick Controls</h3>
      <button disabled={busy} onClick={() => runCommand(() => sendBlockAppCommand(childUid, 'com.instagram.android', 'Instagram'), 'block_app')}>Block Instagram</button>
      <button disabled={busy} onClick={() => runCommand(() => sendUnblockAppCommand(childUid, 'com.instagram.android', 'Instagram'), 'unblock_app')}>Unblock Instagram</button>
      <button disabled={busy} onClick={() => runCommand(() => sendSetLimitCommand(childUid, 'com.instagram.android', 'Instagram', 30), 'set_limit')}>Set Instagram Limit 30m</button>
      <p>Latest command status: <b>{commandStateText}</b></p>
      <p>Statuses: pending / handled / failed</p>
    </div>
    <div className="card"><h3>Pending Time Requests</h3>
      {requests.length === 0 ? <p>No pending requests.</p> : requests.map((req) => <div key={req.id} className="row"><p>{req.deviceName} • {req.appName} • {req.requestedMinutes}m</p>
        <button onClick={() => runRequestAction(() => approveTimeRequest(req.id, req.requestedMinutes), 'Approval')}>Approve</button>
        <button className="danger" onClick={() => runRequestAction(() => denyTimeRequest(req.id), 'Denial')}>Deny</button></div>)}
    </div>
    <div className="card"><h3>Recent Usage (latest 10)</h3>
      {sessions.length === 0 ? <p>No usage sessions.</p> : sessions.map((s) => <div className="row" key={s.id}><span>{s.appName || 'Unknown App'} ({s.appPackage || 'unknown.package'})</span><span>{s.duration || 0} sec</span><span>{s.startTime?.toDate?.()?.toLocaleString?.() || '-'} → {s.endTime?.toDate?.()?.toLocaleString?.() || '-'}</span></div>)}
    </div>
  </div>
}
