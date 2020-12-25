create a new Linode instance (4 cores, 8GB ram) with ubuntu16.04 as the base image.  

wait for the Linode instance to become ready.  

clone the project in the Linode instance: `git clone https://github.com/amirphl/jibri.git`  

run: `git checkout feature/create-linode-when-new-request`  

now fill file **init.sh** environment variables and adjust it to your production server xmpp settings.  

run: `./init.sh`  

run: `sudo apt-get install openjdk-8-jdk maven -y`  

build jibri by running: `mvn package`  

run: `systemctl stop jibri`  

run: `cp target/jibri-8.0-SNAPSHOT-jar-with-dependencies.jar /opt/jitsi/jibri/jibri.jar`  

run: `systemctl start jibri`  

watch jibri logs and check whether everything is ok or not. `tail -f /var/log/jitsi/jibri/log.0.txt`  

go to configuration settings (in Linode panel) and select `GRUB 2` as the kernel. this is very important.

reboot the Linode instance and wait for it to become ready.

now you can scale your pool by adding new Linodes with cloning the Linode instance. also each service request clones a new Linode instance.  

also test the system by running multiple concurrent livestreamings or recordings.  
