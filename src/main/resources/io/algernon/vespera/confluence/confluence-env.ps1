# Sets what `vespera publish` reads, in this PowerShell session only (ADR-308 §3, ADR-318).
#
# Fill in the site, the space and the parent below, then dot-source this file in the session that will run the
# publish:
#
#     . .\confluence-env.ps1
#
# It asks for the account's email and API token each time it is run, and writes neither anywhere. Name the parent by
# its id or by its title, not both: leave the other empty.

$env:VESPERA_CONFLUENCE_BASE_URL = ''      # https://<your-site>
$env:VESPERA_CONFLUENCE_SPACE_KEY = ''     # the key of the space the pages go to
$env:VESPERA_CONFLUENCE_FOLDER_ID = ''     # the id of the page or folder they go under, or:
$env:VESPERA_CONFLUENCE_FOLDER_TITLE = ''  # its title

$env:VESPERA_CONFLUENCE_EMAIL = Read-Host 'Confluence account email'
$secure = Read-Host 'Confluence API token' -AsSecureString
$env:VESPERA_CONFLUENCE_TOKEN = [System.Net.NetworkCredential]::new('', $secure).Password
Remove-Variable secure
