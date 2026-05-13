const toSerializable = (value) => {
  if (value instanceof Error) {
    return { message: value.message, stack: value.stack }
  }

  return value
}

const log = (tag, message, data = {}) => {
  console.log(`[${tag}] ${message}`, toSerializable(data))
}

export const logParentFirebase = (message, data) => log('PARENT_FIREBASE', message, data)
export const logParentCommand = (message, data) => log('PARENT_COMMAND', message, data)
export const logParentRequest = (message, data) => log('PARENT_REQUEST', message, data)
export const logParentChildren = (message, data) => log('PARENT_CHILDREN', message, data)
