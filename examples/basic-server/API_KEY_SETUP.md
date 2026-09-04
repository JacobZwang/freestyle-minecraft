# API Key Configuration

The plugin talks to the [Freestyle v5 API](https://www.freestyle.sh/docs/vms). Get an API key at [dash.freestyle.sh](https://dash.freestyle.sh).

Configuration comes from the environment first, then `freestyle-config.properties`. Pick whichever suits your deployment.

## Environment (preferred)

Nothing to commit, nothing to leak:

```bash
export FREESTYLE_API_KEY="your-actual-api-key"
export FREESTYLE_BASE_VM="minecraft"
```

## Properties file

```bash
cp freestyle-config.properties.example freestyle-config.properties
```

```properties
freestyle.api.key=your-actual-api-key
freestyle.api.url=https://api.freestyle.sh
freestyle.base.vm=minecraft
freestyle.domain.suffix=style.dev
freestyle.idle.timeout.seconds=300
```

`freestyle-config.properties` is gitignored. `freestyle-config.properties.example` is the tracked template — keep secrets out of it.

Restart the Velocity server after changing either.

## The base VM

`freestyle.base.vm` names the VM every world is forked from, by Freestyle id (`vm-…`) or by your account slug. It must already run a Minecraft server on port 25565.

Because Freestyle's edge sits between the player and the server, that VM's `server.properties` needs:

```properties
prevent-proxy-connections=false
```

Without it, an online-mode server rejects every login.

## Verifying

On startup the plugin logs the API URL, the base VM, and the domain suffix it will publish worlds under. A missing or rejected key fails loudly there rather than at first world creation.
