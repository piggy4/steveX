const MinecraftData = require('minecraft-data');

class MCDataManager {
    private mc_data = new Map<string, any>();

    public get(version: string) {
        let data = this.mc_data.get(version);
        if (data === undefined) {
            data = MinecraftData(version);
            this.mc_data.set(version, data);
        }
        return data;
    }
}

const mcDataManager = new MCDataManager();
module.exports = { mcDataManager };
