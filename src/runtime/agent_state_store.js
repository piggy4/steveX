class AgentStateStore {
    constructor() {
        this.states = new Map()
    }

    /**
     * Get a snapshot of an agent state by id.
     * @param {string} id
     * @returns {object|null}
     */
    getState(id) {
        if (!id) return null
        const state = this.states.get(id)
        return state ? { ...state } : null
    }

    /**
     * Patch and store an agent state by id.
     * @param {string} id
     * @param {object} patch
     * @returns {object}
     */
    updateState(id, patch) {
        if (!id) throw new Error('Missing agent id')
        const prev = this.states.get(id) || { id }
        const next = { ...prev, ...patch, id }
        this.states.set(id, next)
        return { ...next }
    }

    /**
     * Return a snapshot array of all agent states.
     * @returns {object[]}
     */
    getAllStates() {
        return [...this.states.values()].map(state => ({ ...state }))
    }
}

module.exports = { AgentStateStore }
