# API Key Configuration

To use the Freestyle API for real VM provisioning:

1. Edit `freestyle-config.properties`
2. Change `vm.provider=local` to `vm.provider=freestyle`  
3. Replace `your-api-key-here` with your actual Freestyle API key
4. Restart the Velocity server

## Example Configuration

```properties
# Use Freestyle API for production
vm.provider=freestyle
freestyle.api.url=https://api.freestyle.sh
freestyle.api.key=your-actual-api-key-here
```

**Note:** When using the Freestyle API, all new servers will be forked from VM `yrtby` as requested.

## Testing Mode

For testing without real VMs, keep:
```properties  
vm.provider=local
```

This will simulate server creation on different localhost ports.