# Hubitat Package Manager deployment

## Install from HPM

1. Open **Apps > Hubitat Package Manager**.
2. Choose **Install** and then **From a URL**.
3. Enter:
   `https://raw.githubusercontent.com/pierowman/Hubitat-School-Status-Manager/master/manifests/packageManifest.json`
4. Complete the HPM installation prompts.
5. Open **Apps > Add User App** and add **School Status Manager**.
6. Configure the app and select **Done**. The app automatically schedules an initial calendar pull after installation.

The HPM URL must point to the raw package JSON. Do not use the GitHub `/blob/master/` page URL.

## Updates

Use **Hubitat Package Manager > Update** when a new package version is available. After the code update, open the installed **School Status Manager** app and select **Done** so its `updated()` lifecycle recreates schedules and triggers the startup refresh.

## Repository files

- `manifests/packageManifest.json` - HPM package definition
- `manifests/repository.json` - repository metadata used for package discovery/publishing
- `apps/SchoolStatusManager.groovy` - app source
- `drivers/SchoolStatusDevice.groovy` - driver source
