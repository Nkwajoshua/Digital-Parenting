import { after, before, beforeEach, describe, test } from 'node:test'
import { readFile } from 'node:fs/promises'
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing'
import {
  Timestamp,
  collection,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore'

const PROJECT_ID = 'demo-digital-parenting-rules'
let testEnv

const parentDb = (uid) => testEnv.authenticatedContext(uid, {
  email: `${uid}@example.test`,
  email_verified: true,
  firebase: {
    sign_in_provider: 'password',
    identities: { email: [`${uid}@example.test`] },
  },
}).firestore()

const childDb = (uid) => testEnv.authenticatedContext(uid, {
  provider_id: 'anonymous',
  firebase: {
    sign_in_provider: 'anonymous',
    identities: {},
  },
}).firestore()

async function seedFixtures() {
  const now = Timestamp.now()
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore()

    await Promise.all([
      setDoc(doc(db, 'parents', 'parentA'), {
        uid: 'parentA', email: 'parentA@example.test', updatedAt: now,
      }),
      setDoc(doc(db, 'parents', 'parentB'), {
        uid: 'parentB', email: 'parentB@example.test', updatedAt: now,
      }),
      setDoc(doc(db, 'children', 'childA'), {
        parentUid: 'parentA', paired: true, pairingCode: '111111',
        pairedAt: now, updatedAt: now, deviceName: 'Child A', platform: 'android',
        monitoringActive: true,
      }),
      setDoc(doc(db, 'children', 'childB'), {
        parentUid: 'parentB', paired: true, pairingCode: '222222',
        pairedAt: now, updatedAt: now, deviceName: 'Child B', platform: 'android',
        monitoringActive: true,
      }),
      setDoc(doc(db, 'pairing_codes', '111111'), {
        code: '111111', parentUid: 'parentA', status: 'pending',
        createdAt: now, updatedAt: now,
        expiresAt: Timestamp.fromMillis(now.toMillis() + 15 * 60 * 1000),
        usedByChildUid: null, usedAt: null,
      }),
      setDoc(doc(db, 'pairing_codes', '222222'), {
        code: '222222', parentUid: 'parentB', status: 'pending',
        createdAt: now, updatedAt: now,
        expiresAt: Timestamp.fromMillis(now.toMillis() + 15 * 60 * 1000),
        usedByChildUid: null, usedAt: null,
      }),
      setDoc(doc(db, 'children', 'childA', 'commands', 'pendingCommand'), {
        parentUid: 'parentA', childUid: 'childA', type: 'block_app',
        appPackage: 'com.example.app', appName: 'Example', status: 'pending', createdAt: now,
      }),
      setDoc(doc(db, 'children', 'childA', 'commands', 'handledCommand'), {
        parentUid: 'parentA', childUid: 'childA', type: 'block_app',
        appPackage: 'com.example.app', appName: 'Example', status: 'handled',
        createdAt: now, handledAt: now,
      }),
      setDoc(doc(db, 'time_requests', 'pendingRequestA'), {
        childUid: 'childA', deviceName: 'Child A', appName: 'Example',
        appPackage: 'com.example.app', requestedMinutes: 15, approvedMinutes: null,
        status: 'pending', parentResponse: null, createdAt: now, resolvedAt: null,
      }),
      setDoc(doc(db, 'time_requests', 'approvedRequestA'), {
        childUid: 'childA', deviceName: 'Child A', appName: 'Example',
        appPackage: 'com.example.app', requestedMinutes: 15, approvedMinutes: 15,
        status: 'approved', parentResponse: 'approved', createdAt: now, resolvedAt: now,
      }),
      setDoc(doc(db, 'usage_sessions', 'childA', 'sessions', 'sessionA'), {
        packageName: 'com.example.app', appName: 'Example',
        startTime: now.toMillis() - 60_000, endTime: now.toMillis(),
        duration: 60_000, durationSeconds: 60, syncedAt: now.toMillis(),
      }),
      setDoc(doc(db, 'parent_notifications', 'notificationA'), {
        parentUid: 'parentA', childUid: 'childA', type: 'test', severity: 'info',
        title: 'Test', body: 'Test notification', createdAt: now, read: false,
      }),
      setDoc(doc(db, 'parent_notifications', 'notificationB'), {
        parentUid: 'parentB', childUid: 'childB', type: 'test', severity: 'info',
        title: 'Test B', body: 'Other notification', createdAt: now, read: false,
      }),
      setDoc(doc(db, 'command_audit', 'auditA'), {
        parentUid: 'parentA', childUid: 'childA', commandId: 'pendingCommand',
        type: 'block_app', createdAt: now, auditedAt: now,
      }),
      setDoc(doc(db, 'control_rate_limits', 'pairing_parentA'), {
        parentUid: 'parentA', action: 'createPairingCode', lastIssuedAt: now,
      }),
    ])
  })
}

before(async () => {
  const rules = await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8')
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules },
  })
})

beforeEach(async () => {
  await testEnv.clearFirestore()
  await seedFixtures()
})

after(async () => {
  await testEnv.cleanup()
})

describe('Digital Parenting Firestore authorization', { concurrency: false }, () => {
  test('Parent can read own profile but not another Parent profile', async () => {
    await assertSucceeds(getDoc(doc(parentDb('parentA'), 'parents', 'parentA')))
    await assertFails(getDoc(doc(parentDb('parentA'), 'parents', 'parentB')))
  })

  test('unauthenticated client cannot read a Parent profile', async () => {
    const db = testEnv.unauthenticatedContext().firestore()
    await assertFails(getDoc(doc(db, 'parents', 'parentA')))
  })

  test('Parent child-list query must prove ownership and paired state', async () => {
    const db = parentDb('parentA')
    const safeQuery = query(
      collection(db, 'children'),
      where('parentUid', '==', 'parentA'),
      where('paired', '==', true),
      orderBy('updatedAt', 'desc'),
    )
    await assertSucceeds(getDocs(safeQuery))

    const underConstrainedQuery = query(
      collection(db, 'children'),
      where('parentUid', '==', 'parentA'),
    )
    await assertFails(getDocs(underConstrainedQuery))
  })

  test('Child can read its own child record but not another child record', async () => {
    await assertSucceeds(getDoc(doc(childDb('childA'), 'children', 'childA')))
    await assertFails(getDoc(doc(childDb('childA'), 'children', 'childB')))
  })

  test('Child can update health fields but cannot rewrite ownership', async () => {
    const db = childDb('childA')
    await assertSucceeds(updateDoc(doc(db, 'children', 'childA'), {
      batteryLevel: 72,
      monitoringActive: true,
      updatedAt: Timestamp.now(),
    }))
    await assertFails(updateDoc(doc(db, 'children', 'childA'), {
      parentUid: 'parentB',
    }))
  })

  test('Parent can update child profile settings but not child heartbeat fields', async () => {
    const db = parentDb('parentA')
    await assertSucceeds(updateDoc(doc(db, 'children', 'childA'), {
      childName: 'Alex',
      updatedAt: Timestamp.now(),
    }))
    await assertFails(updateDoc(doc(db, 'children', 'childA'), {
      batteryLevel: 15,
    }))
  })

  test('clients cannot establish Child ownership directly', async () => {
    const now = Timestamp.now()
    await assertFails(setDoc(doc(childDb('childC'), 'children', 'childC'), {
      parentUid: 'parentA', paired: true, pairingCode: '333333',
      pairedAt: now, updatedAt: now,
    }))
    await assertFails(setDoc(doc(parentDb('parentA'), 'children', 'childC'), {
      parentUid: 'parentA', paired: true, pairingCode: '333333',
      pairedAt: now, updatedAt: now,
    }))
  })

  test('pairing codes are server-write-only and cannot be listed', async () => {
    const now = Timestamp.now()
    const parentA = parentDb('parentA')
    await assertSucceeds(getDoc(doc(parentA, 'pairing_codes', '111111')))
    await assertFails(getDoc(doc(parentA, 'pairing_codes', '222222')))
    await assertFails(getDocs(collection(parentA, 'pairing_codes')))
    await assertFails(setDoc(doc(parentA, 'pairing_codes', '333333'), {
      code: '333333', parentUid: 'parentA', status: 'pending', createdAt: now,
    }))
  })

  test('command creation is server-only and Child can acknowledge only a pending command', async () => {
    const now = Timestamp.now()
    const parentA = parentDb('parentA')
    const childA = childDb('childA')

    await assertFails(setDoc(doc(parentA, 'children', 'childA', 'commands', 'directParentCommand'), {
      parentUid: 'parentA', childUid: 'childA', type: 'block_app',
      appPackage: 'com.example.app', status: 'pending', createdAt: now,
    }))
    await assertFails(setDoc(doc(childA, 'children', 'childA', 'commands', 'directChildCommand'), {
      parentUid: 'parentA', childUid: 'childA', type: 'block_app',
      appPackage: 'com.example.app', status: 'pending', createdAt: now,
    }))

    await assertSucceeds(updateDoc(doc(childA, 'children', 'childA', 'commands', 'pendingCommand'), {
      status: 'handled', handledAt: now,
    }))
    await assertFails(updateDoc(doc(parentA, 'children', 'childA', 'commands', 'handledCommand'), {
      status: 'failed', handledAt: now,
    }))
    await assertFails(updateDoc(doc(childA, 'children', 'childA', 'commands', 'handledCommand'), {
      status: 'failed', handledAt: now,
    }))
  })

  test('paired Child can create a bounded time request; unpaired or oversized requests fail', async () => {
    const now = Timestamp.now()
    const validRequest = {
      childUid: 'childA', deviceName: 'Child A', appName: 'Example',
      appPackage: 'com.example.app', requestedMinutes: 30, approvedMinutes: null,
      status: 'pending', parentResponse: null, createdAt: now, resolvedAt: null,
    }
    await assertSucceeds(setDoc(doc(childDb('childA'), 'time_requests', 'newValidRequest'), validRequest))
    await assertFails(setDoc(doc(childDb('childC'), 'time_requests', 'unpairedRequest'), {
      ...validRequest, childUid: 'childC',
    }))
    await assertFails(setDoc(doc(childDb('childA'), 'time_requests', 'oversizedRequest'), {
      ...validRequest, requestedMinutes: 241,
    }))
  })

  test('Parent cannot resolve a time request directly; Child can only apply an approved request', async () => {
    const now = Timestamp.now()
    await assertFails(updateDoc(doc(parentDb('parentA'), 'time_requests', 'pendingRequestA'), {
      status: 'approved', parentResponse: 'approved', approvedMinutes: 15, resolvedAt: now,
    }))
    await assertSucceeds(updateDoc(doc(childDb('childA'), 'time_requests', 'approvedRequestA'), {
      status: 'applied', appliedAt: now,
    }))
    await assertFails(updateDoc(doc(childDb('childB'), 'time_requests', 'approvedRequestA'), {
      status: 'applied', appliedAt: now,
    }))
  })

  test('usage sessions are writable only by the paired Child that owns the path', async () => {
    const data = {
      packageName: 'com.example.new', appName: 'New',
      startTime: 1_000, endTime: 61_000, duration: 60_000,
      durationSeconds: 60, syncedAt: 61_000,
    }
    await assertSucceeds(setDoc(doc(childDb('childA'), 'usage_sessions', 'childA', 'sessions', 'newSession'), data))
    await assertFails(setDoc(doc(childDb('childC'), 'usage_sessions', 'childC', 'sessions', 'newSession'), data))
    await assertFails(setDoc(doc(parentDb('parentA'), 'usage_sessions', 'childA', 'sessions', 'parentSession'), data))
    await assertSucceeds(getDoc(doc(parentDb('parentA'), 'usage_sessions', 'childA', 'sessions', 'sessionA')))
    await assertFails(getDoc(doc(parentDb('parentB'), 'usage_sessions', 'childA', 'sessions', 'sessionA')))
  })

  test('parent notifications are server-created; owning Parent may read and mark read only', async () => {
    const parentA = parentDb('parentA')
    await assertSucceeds(getDoc(doc(parentA, 'parent_notifications', 'notificationA')))
    await assertFails(getDoc(doc(parentA, 'parent_notifications', 'notificationB')))
    await assertFails(setDoc(doc(parentA, 'parent_notifications', 'directNotification'), {
      parentUid: 'parentA', title: 'Direct', read: false,
    }))
    await assertSucceeds(updateDoc(doc(parentA, 'parent_notifications', 'notificationA'), {
      read: true,
    }))
    await assertFails(updateDoc(doc(parentA, 'parent_notifications', 'notificationA'), {
      title: 'Tampered',
    }))
  })

  test('command audit is append-only to clients and readable only by owning Parent', async () => {
    await assertSucceeds(getDoc(doc(parentDb('parentA'), 'command_audit', 'auditA')))
    await assertFails(getDoc(doc(parentDb('parentB'), 'command_audit', 'auditA')))
    await assertFails(setDoc(doc(parentDb('parentA'), 'command_audit', 'directAudit'), {
      parentUid: 'parentA', childUid: 'childA', commandId: 'x', type: 'block_app',
    }))
  })

  test('server-only control rate limits are inaccessible to every client role', async () => {
    await assertFails(getDoc(doc(parentDb('parentA'), 'control_rate_limits', 'pairing_parentA')))
    await assertFails(getDoc(doc(childDb('childA'), 'control_rate_limits', 'pairing_parentA')))
    await assertFails(updateDoc(doc(parentDb('parentA'), 'control_rate_limits', 'pairing_parentA'), {
      action: 'tampered',
    }))
  })
})
