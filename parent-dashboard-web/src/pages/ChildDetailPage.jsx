import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
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
      <input name="appName" placeholder="appName" value={values.appName} onChange={onChange} />
      <input name="appPackage" placeholder="appPackage" value={values.appPackage} onChange={onChange} />
      <input name="maxMinutes" type="number" min="1" placeholder="maxMinutes" value={values.maxMinutes} onChange={onChange} />
    </div>
    <div className="button-row">
      <button disabled={busy} onClick={() => onAction('block_app')}>Block App</button>
      <button disabled={busy} onClick={() => onAction('unblock_app')}>Unblock App</button>
      <button disabled={busy} onClick={() => onAction('set_limit')}>Set Limit</button>
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
    if (!appName || !appPackage) {
      setFeedback('appName and appPackage are required')
      return
    }

    const handlers = {
      block_app: () => sendBlockAppCommand(childUid, appPackage, appName),
      unblock_app: () => sendUnblockAppCommand(childUid, appPackage, appName),
      set_limit: () => sendSetLimitCommand(childUid, appPackage, appName, Number(maxMinutes || 0)),
    }

    try {
      setCommandBusy(true)
      await handlers[action]()
      setFeedback(`${action} queued successfully`)
      logParentCommand('Command queued', { childUid, action, appName, appPackage, maxMinutes })
    } catch (error) {
      logParentCommand('Command failed', { childUid, action, error })
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
    <h1>Child Detail</h1>
    {feedback && <p className="muted">{feedback}</p>}

    <div className="card">
      <h3>Selected Child</h3>
      <p><b>Name:</b> {selectedChild?.deviceName || 'Unknown device'}</p>
      <p><b>Platform:</b> {selectedChild?.platform || 'unknown'}</p>
      <p><b>Monitoring:</b> <span className={selectedChild?.monitoringActive ? 'badge active' : 'badge offline'}>{selectedChild?.monitoringActive ? 'Active' : 'Inactive'}</span></p>
      <p><b>Latest heartbeat:</b> {selectedChild?.lastHeartbeatAt?.toDate?.()?.toLocaleString?.() || 'N/A'}</p><p><b>Battery:</b> {typeof selectedChild?.batteryLevel === 'number' ? `${selectedChild.batteryLevel}%` : 'N/A'} {selectedChild?.charging ? '(charging)' : ''}</p><p><b>App Version:</b> {selectedChild?.appVersion || 'N/A'}</p><p><b>Last request result:</b> {latestRequest?.status || 'N/A'}</p>
      <p><b>UID:</b> <code>{childUid}</code> <button onClick={() => navigator.clipboard?.writeText(childUid)}>Copy UID</button></p>
    </div>

    <div className="card">
      <h3>Quick App Controls</h3>
      <AppControlForm values={form} onChange={onFormChange} onAction={onCommandAction} busy={commandBusy} />
    </div>

    <div className="card"><h3>Command History (latest 10)</h3>
      {commands.length === 0 ? <p>No commands yet.</p> : commands.map((cmd) => <div key={cmd.id} className="row wrap"><span><b>{cmd.type}</b> • {cmd.appName || 'n/a'} • {cmd.appPackage || 'n/a'}</span><span className={`badge ${cmd.status === 'failed' ? 'danger-badge' : cmd.status === 'handled' ? 'active' : 'offline'}`}>{cmd.status || 'pending'}</span><span>{cmd.createdAt?.toDate?.()?.toLocaleString?.() || 'N/A'}</span><span>{cmd.handledAt?.toDate?.()?.toLocaleString?.() || '-'}</span><span>{cmd.errorMessage || ''}</span></div>)}
      <p className="muted">Latest command status: {latestCommand?.status || 'N/A'}{commandLatency !== null ? ` · latency ${commandLatency}s` : ''}</p>
    </div>

    <div className="card"><h3>Pending Time Requests</h3>
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

    <div className="card"><h3>Recent Usage Preview</h3>
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
