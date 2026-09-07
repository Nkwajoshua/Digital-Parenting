import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  approveTimeRequest,
  denyTimeRequest,
  listenChildren,
  listenPendingTimeRequests,
  listenRecentCommands,
  listenRecentUsageSessions,
  sendBlockAppCommand,
  sendSetLimitCommand,
  sendUnblockAppCommand,
} from '../services/dashboardApi'
import { logParentChildren, logParentCommand, logParentRequest } from '../services/logger'
import { useAuth } from '../services/authContext'

function AppControlForm({ values, onChange, onAction, busy }) {
  return <div>
    <div className="form-grid">
      <label>App name<input name="appName" placeholder="e.g. Instagram" value={values.appName} onChange={onChange} /></label>
      <label>Android package<input name="appPackage" placeholder="e.g. com.instagram.android" value={values.appPackage} onChange={onChange} /></label>
      <label>Daily limit<input name="maxMinutes" type="number" min="1" placeholder="Minutes" value={values.maxMinutes} onChange={onChange} /></label>
    </div>
    <div className="button-row">
      <button className="danger" disabled={busy} onClick={() => onAction('block_app')}>{busy ? 'Sending…' : 'Block app'}</button>
      <button className="secondary" disabled={busy} onClick={() => onAction('unblock_app')}>Unblock app</button>
      <button disabled={busy} onClick={() => onAction('set_limit')}>Set daily limit</button>
    </div>
  </div>
}

export default function ChildDetailPage() {
  const { childUid } = useParams()
  const { user, devBypass } = useAuth()
  const [children, setChildren] = useState([])
  const [requests, setRequests] = useState([])
  const [sessions, setSessions] = useState([])
  const [commands, setCommands] = useState([])
  const [feedback, setFeedback] = useState('')
  const [commandBusy, setCommandBusy] = useState(false)
  const [requestBusyId, setRequestBusyId] = useState('')
  const [form, setForm] = useState({ appName: 'Instagram', appPackage: 'com.instagram.android', maxMinutes: 30 })

  useEffect(() => {
    const unsubChildren = listenChildren(devBypass ? null : user?.uid, setChildren, (err) => logParentChildren('Child detail children listener failed', { err, childUid }))
    const unsubReq = listenPendingTimeRequests(devBypass ? [] : [childUid], (rows) => setRequests(rows.filter((r) => r.childUid === childUid)), console.error)
    const unsubSessions = listenRecentUsageSessions(childUid, setSessions, console.error)
    const unsubCommands = listenRecentCommands(childUid, setCommands, console.error)

    return () => { unsubChildren(); unsubReq(); unsubSessions(); unsubCommands() }
  }, [childUid, user?.uid, devBypass])

  const selectedChild = useMemo(() => children.find((c) => c.id === childUid), [children, childUid])
  const latestCommand = commands[0]
  const latestRequest = requests[0]
  const commandLatency = latestCommand?.handledAt?.toDate?.() && latestCommand?.createdAt?.toDate?.() ? Math.round((latestCommand.handledAt.toDate() - latestCommand.createdAt.toDate()) / 1000) : null

  const onFormChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: name === 'maxMinutes' ? Number(value) : value }))
  }

  const onCommandAction = async (action) => {
    const { appName, appPackage, maxMinutes } = form
    const parentUid = user?.uid

    if (!parentUid) {
      setFeedback('Sign in as a parent before sending commands.')
      return
    }

    if (!appName || !appPackage) {
      setFeedback('appName and appPackage are required')
      return
    }

    if (action === 'set_limit' && Number(maxMinutes) <= 0) {
      setFeedback('maxMinutes must be greater than zero')
      return
    }

    const handlers = {
      block_app: () => sendBlockAppCommand(parentUid, childUid, appPackage, appName),
      unblock_app: () => sendUnblockAppCommand(parentUid, childUid, appPackage, appName),
      set_limit: () => sendSetLimitCommand(parentUid, childUid, appPackage, appName, Number(maxMinutes)),
    }

    try {
      setCommandBusy(true)
      await handlers[action]()
      setFeedback(`${action} queued successfully`)
      logParentCommand('Command queued', { parentUid, childUid, action, appName, appPackage, maxMinutes })
    } catch (error) {
      logParentCommand('Command failed', { parentUid, childUid, action, error })
      setFeedback(`${action} failed: ${error?.message || 'unknown error'}`)
    } finally {
      setCommandBusy(false)
    }
  }

  const runRequestAction = async (req, action) => {
    if (requestBusyId) return
    try {
      setRequestBusyId(req.id)
      if (action === 'approve') await approveTimeRequest(req.id, req.requestedMinutes)
      if (action === 'deny') await denyTimeRequest(req.id)
      setFeedback(`Request ${action}d successfully`)
      logParentRequest('Request updated', { action, requestId: req.id, childUid })
    } catch (error) {
      setFeedback(`Request ${action} failed: ${error?.message || 'unknown error'}`)
      logParentRequest('Request update failed', { action, requestId: req.id, childUid, error })
    } finally {
      setRequestBusyId('')
    }
  }

  return <div>
    <Link className="back-link" to="/children">← All devices</Link>
    <header className="page-header"><div><span className="eyebrow">Device management</span><h1>{selectedChild?.deviceName || 'Child device'}</h1><p>Review live status, set app boundaries, and respond to requests.</p></div><span className={selectedChild?.monitoringActive ? 'badge active' : 'badge offline'}>{selectedChild?.monitoringActive ? 'Protection active' : 'Protection inactive'}</span></header>
    {feedback && <div className={`alert ${feedback.includes('failed') || feedback.includes('required') ? 'error' : 'success'}`} role="status">{feedback}</div>}

    <div className="card">
      <div className="section-heading"><div><h2>Device overview</h2><p>Latest information received from the Child app.</p></div></div>
      <p><b>Name:</b> {selectedChild?.deviceName || 'Unknown device'}</p>
      <p><b>Platform:</b> {selectedChild?.platform || 'unknown'}</p>
      <p><b>Monitoring:</b> <span className={selectedChild?.monitoringActive ? 'badge active' : 'badge offline'}>{selectedChild?.monitoringActive ? 'Active' : 'Inactive'}</span></p>
      <p><b>Latest heartbeat:</b> {selectedChild?.lastHeartbeatAt?.toDate?.()?.toLocaleString?.() || 'N/A'}</p><p><b>Battery:</b> {typeof selectedChild?.batteryLevel === 'number' ? `${selectedChild.batteryLevel}%` : 'N/A'} {selectedChild?.charging ? '(charging)' : ''}</p><p><b>App Version:</b> {selectedChild?.appVersion || 'N/A'}</p><p><b>Last request result:</b> {latestRequest?.status || 'N/A'}</p>
      <p><b>UID:</b> <code>{childUid}</code> <button onClick={() => navigator.clipboard?.writeText(childUid)}>Copy UID</button></p>
    </div>

    <div className="card">
      <h2>App controls</h2><p className="muted">Enter the app identity exactly as installed on the Child device.</p>
      <AppControlForm values={form} onChange={onFormChange} onAction={onCommandAction} busy={commandBusy} />
    </div>

    <div className="card"><h2>Recent commands</h2><p className="muted">The 10 most recent actions sent to this device.</p>
      {commands.length === 0 ? <p>No commands yet.</p> : commands.map((cmd) => <div key={cmd.id} className="row wrap"><span><b>{cmd.type}</b> • {cmd.appName || 'n/a'} • {cmd.appPackage || 'n/a'}</span><span className={`badge ${cmd.status === 'failed' ? 'danger-badge' : cmd.status === 'handled' ? 'active' : 'offline'}`}>{cmd.status || 'pending'}</span><span>{cmd.createdAt?.toDate?.()?.toLocaleString?.() || 'N/A'}</span><span>{cmd.handledAt?.toDate?.()?.toLocaleString?.() || '-'}</span><span>{cmd.errorMessage || ''}</span></div>)}
      <p className="muted">Latest command status: {latestCommand?.status || 'N/A'}{commandLatency !== null ? ` · latency ${commandLatency}s` : ''}</p>
    </div>

    <div className="card"><h2>Pending time requests</h2>
      {requests.length === 0 ? <p>No pending requests.</p> : requests.map((req) => {
        const isBusy = requestBusyId === req.id
        return <div key={req.id} className="row wrap"><span>{req.deviceName || selectedChild?.deviceName || 'Device'} • {req.appName} • {req.requestedMinutes}m • {req.createdAt?.toDate?.()?.toLocaleString?.() || 'N/A'}</span>
          <div>
            <button disabled={Boolean(requestBusyId)} onClick={() => runRequestAction(req, 'approve')}>{isBusy ? 'Approving…' : 'Approve'}</button>
            <button className="danger" disabled={Boolean(requestBusyId)} onClick={() => runRequestAction(req, 'deny')}>{isBusy ? 'Denying…' : 'Deny'}</button>
          </div>
        </div>
      })}
    </div>

    <div className="card"><h2>Recent app usage</h2>
      {sessions.length === 0 ? <p>No usage sessions found.</p> : sessions.map((s) => <div className="row wrap" key={s.id}><span>{s.appName || 'Unknown App'} ({s.appPackage || 'unknown.package'})</span><span>{s.duration || 0} sec</span><span>{s.startTime?.toDate?.()?.toLocaleString?.() || '-'} → {s.endTime?.toDate?.()?.toLocaleString?.() || '-'}</span></div>)}
    </div>

    <div className="card dev-only">
      <h3>Manual Test Guide (Development)</h3>
      <ol>
        <li>Ensure child app is running.</li>
        <li>Send <code>set_limit</code>.</li>
        <li>Send <code>block_app</code>.</li>
        <li>Confirm command status becomes <code>handled</code>.</li>
        <li>Test request approval.</li>
      </ol>
    </div>
  </div>
}
