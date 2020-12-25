#!/bin/bash

# fill variables before running the scripts
# ----------------------
# ----------------------
LINODE_PERSONAL_ACCESS_TOKEN=
XMPP_SERVER_HOST=
XMPP_DOMAIN=
JIBRI_USERNAME=
JIBRI_PASSWORD=
RECORDER_USERNAME=
RECORDER_PASSWORD=


# ----------------------
# ----------------------
RANDOM=`date +%s%N | cut -b10-19`

sudo apt-get update -y
# sudo apt-get install linux-image-extra-virtual -y
sudo apt-get install alsa-utils -y
echo "snd-aloop" >> /etc/modules
modprobe snd-aloop
lsmod | grep snd_aloop
curl -sS -o - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add
sudo echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list
sudo apt-get update -y
sudo apt-get install google-chrome-stable -y
sudo mkdir -p /etc/opt/chrome/policies/managed
echo '{ "CommandLineFlagSecurityWarningsEnabled": false }' >>/etc/opt/chrome/policies/managed/managed_policies.json
sudo apt-get install unzip -y
CHROME_DRIVER_VERSION=`curl -sS chromedriver.storage.googleapis.com/LATEST_RELEASE`
wget -N http://chromedriver.storage.googleapis.com/$CHROME_DRIVER_VERSION/chromedriver_linux64.zip -P ~/
unzip ~/chromedriver_linux64.zip -d ~/
rm ~/chromedriver_linux64.zip
sudo mv -f ~/chromedriver /usr/local/bin/chromedriver
sudo chown root:root /usr/local/bin/chromedriver
sudo chmod 0755 /usr/local/bin/chromedriver
sudo apt-get install default-jre-headless ffmpeg icewm xdotool xserver-xorg-input-void xserver-xorg-video-dummy -y
wget -qO - https://download.jitsi.org/jitsi-key.gpg.key | sudo apt-key add -
sudo sh -c "echo 'deb https://download.jitsi.org stable/' > /etc/apt/sources.list.d/jitsi-stable.list"
sudo apt-get update -y
sudo apt-get install apt-transport-https -y
sudo apt-get update -y
sudo apt-get install jibri -y
sudo usermod -aG adm,audio,video,plugdev jibri
echo 'options snd-aloop enable=1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1 index=0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29' >> /etc/modprobe.d/alsa-loopback.conf
modprobe snd_aloop
mkdir -p /home/jibri/recordings
echo '#!/bin/bash' >> /home/jibri/final.sh
sudo chmod a+x /home/jibri/final.sh
sudo chown -R jibri:jibri /home/jibri/

sudo systemctl enable jibri
sed -i.bak "/[Service]$/,/^User=jibri$/{/User=jibri$/i\
Environment=\"LINODE_PERSONAL_ACCESS_TOKEN=$LINODE_PERSONAL_ACCESS_TOKEN\"
}" /etc/systemd/system/jibri.service


CONFIG="
{
    \"recording_directory\":\"/home/jibri/recordings\",
    // The path to the script which will be run on completed recordings
    \"finalize_recording_script_path\": \"/home/jibri/final.sh\",
    \"xmpp_environments\": [
        {
            // A friendly name for this environment which can be used
            //  for logging, stats, etc.
            \"name\": \"prod environment\",
            // The hosts of the XMPP servers to connect to as part of
            //  this environment
            \"xmpp_server_hosts\": [
                \"$XMPP_SERVER_HOST\"
            ],
            \"xmpp_domain\": \"$XMPP_DOMAIN\",
            // Jibri will login to the xmpp server as a privileged user 
            \"control_login\": {
                \"domain\": \"auth.$XMPP_DOMAIN\",
                // The credentials for logging in
                \"username\": \"$JIBRI_USERNAME\",
                \"password\": \"$JIBRI_PASSWORD\"
            },
            // Using the control_login information above, Jibri will join 
            //  a control muc as a means of announcing its availability 
            //  to provide services for a given environment
            \"control_muc\": {
                \"domain\": \"internal.auth.$XMPP_DOMAIN\",
                \"room_name\": \"JibriBrewery\",
		// MUST be unic for every instanse
                \"nickname\": \"jibri-instanse-$RANDOM\"
            },
            // All participants in a call join a muc so they can exchange
            //  information.  Jibri can be instructed to join a special muc
            //  with credentials to give it special abilities (e.g. not being
            //  displayed to other users like a normal participant)
            \"call_login\": {
                \"domain\": \"recorder.$XMPP_DOMAIN\",
                \"username\": \"$RECORDER_USERNAME\",
                \"password\": \"$RECORDER_PASSWORD\"
            },
            // When jibri gets a request to start a service for a room, the room
            //  jid will look like:
            //  roomName@optional.prefixes.subdomain.xmpp_domain
            //  build the url for the call by transforming that into:
            //  https://xmpp_domain/subdomain/roomName
            // So if there are any prefixes in the jid (like jitsi meet, which
            //  has its participants join a muc at conference.xmpp_domain) then
            //  list that prefix here so it can be stripped out to generate
            //  the call url correctly
            \"room_jid_domain_string_to_strip_from_start\": \"conference.\",
            // The amount of time, in minutes, a service is allowed to continue.
            //  Once a service has been running for this long, it will be
            //  stopped (cleanly).  A value of 0 means an indefinite amount
            //  of time is allowed
            \"usage_timeout\": \"240\"
        }
    ]
}
"

sudo echo "$CONFIG" > /etc/jitsi/jibri/config.json

sudo systemctl daemon-reload
sudo systemctl restart jibri

