# axcdevops
remote run devops commands built by axcboot

# server

- config: config server port in resources/config.conf
- run: java -jar axcdevops.jar server

# client

- config: config server ip, port, and client name
- run: java -jar axcdeops.jar client

# server shell

- format: [client name] [command] [command parameters]

# command

- ls: list all active client, e.g. ls
- isactive: show if client is active, e.g. [client name] isactive
- download: order client to download remote file, e.g. [client name] download [remote url] [dir to save] [file name to save]
- execute: order client to run command in Windows CMD, [client name] execute java -jar a.jar
