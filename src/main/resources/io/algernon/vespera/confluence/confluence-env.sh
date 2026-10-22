# Sets what `vespera publish` reads, in this shell only (ADR-308 §3).
#
# Fill in the site, the space and the parent below, then source this file in the shell that will run the publish:
#
#     . ./confluence-env.sh
#
# It asks for the account's email and API token each time it is sourced, and writes neither anywhere. Name the parent
# by its id or by its title, not both: leave the other empty.

export VESPERA_CONFLUENCE_BASE_URL=''      # https://<your-site>
export VESPERA_CONFLUENCE_SPACE_KEY=''     # the key of the space the pages go to
export VESPERA_CONFLUENCE_FOLDER_ID=''     # the id of the page or folder they go under, or:
export VESPERA_CONFLUENCE_FOLDER_TITLE=''  # its title

printf 'Confluence account email: '
read -r VESPERA_CONFLUENCE_EMAIL
printf 'Confluence API token: '
stty -echo 2>/dev/null
read -r VESPERA_CONFLUENCE_TOKEN
stty echo 2>/dev/null
printf '\n'
export VESPERA_CONFLUENCE_EMAIL VESPERA_CONFLUENCE_TOKEN
