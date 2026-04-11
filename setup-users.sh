#!/bin/bash
# -------------------------------------------------------
# Earthdawn — Basic Auth User Setup
# Run this script once on the server to create the 5 users.
# It writes BASIC_AUTH_USERS into your .env file.
# -------------------------------------------------------

set -e

ENV_FILE=".env"
USERS_STRING=""

# Hashes a user:password pair with htpasswd (uses Docker so no extra tools needed)
hash_user() {
    local username="$1"
    local password="$2"
    # htpasswd -nb produces "user:$apr1$..." — the $-signs must be doubled for docker-compose
    docker run --rm httpd:alpine htpasswd -nb "$username" "$password" | tr -d '\r' | sed 's/\$/\$\$/g'
}

add_user() {
    local entry
    entry=$(hash_user "$1" "$2")
    if [ -z "$USERS_STRING" ]; then
        USERS_STRING="$entry"
    else
        USERS_STRING="$USERS_STRING,$entry"
    fi
    echo "  ✓ User '$1' added."
}

echo ""
echo "=== Earthdawn Basic Auth Setup ==="
echo "Enter username + password for each of your 5 players."
echo "Passwords are hashed immediately and never stored in plain text."
echo ""

for i in 1 2 3 4 5; do
    read -rp "Username $i: " USERNAME
    read -rsp "Password $i: " PASSWORD
    echo ""
    add_user "$USERNAME" "$PASSWORD"
done

echo ""
echo "Writing BASIC_AUTH_USERS to $ENV_FILE ..."

# Update existing entry or append
if grep -q "^BASIC_AUTH_USERS=" "$ENV_FILE" 2>/dev/null; then
    # Replace the existing line (use | as sed delimiter to avoid conflicts with $ and /)
    sed -i "s|^BASIC_AUTH_USERS=.*|BASIC_AUTH_USERS=$USERS_STRING|" "$ENV_FILE"
else
    echo "BASIC_AUTH_USERS=$USERS_STRING" >> "$ENV_FILE"
fi

echo ""
echo "Done! Now restart the frontend container to apply the change:"
echo ""
echo "  docker compose -f docker-compose.prod.yml up -d --force-recreate earthdawn-frontend"
echo ""
