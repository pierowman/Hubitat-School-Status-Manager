# HPM publishing and installation

## Publish

1. Create a public GitHub repository named `Hubitat-School-Status-Manager` under the `Pierowman` account.
2. Upload this folder's contents to the repository root, preserving the `apps`, `drivers`, and `manifests` folders.
3. Confirm that each raw URL in `manifests/packageManifest.json` and `manifests/repository.json` loads successfully.
4. Validate both JSON files with a JSON validator.
5. In HPM, test an installation using the package manifest URL:
   `https://raw.githubusercontent.com/Pierowman/Hubitat-School-Status-Manager/main/manifests/packageManifest.json`
6. For public HPM discovery, submit the repository manifest to the HubitatCommunity package-repositories project following its current contribution process.

## Existing manual installation

The HPM release uses the author `Pierowman` and namespace `pierowman`. Versions through 1.5.0 used the prior namespace. Record your configuration, remove the old installed app and its generated child device, remove the old app/driver code, then install this package through HPM and configure it again. The cache is rebuilt automatically after a successful calendar refresh.
