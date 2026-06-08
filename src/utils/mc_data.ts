const MinecraftData = require('minecraft-data');

class MCDataManager {
    private mc_data = new Map<string, any>();

    public get(version: string) {
        const data = this.mc_data.get(version);
        if (data === undefined) {
            this.mc_data.set(version, MinecraftData(version));
        }
        return data;
    }
}

const mcDataManager = new MCDataManager();
module.exports = { mcDataManager };
