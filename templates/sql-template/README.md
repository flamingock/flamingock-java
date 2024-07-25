# Setup
1. docker pull mysql/mysql-server:8.0.32
2. `docker run --name='my_sql_container' -d -p 3306:3306 mysql/mysql-server`
3. `docker logs my_sql_container` -> Pick password where it says "[Entrypoint] GENERATED ROOT PASSWORD : ..."
4. `docker exec -it my_sql_container bash`
5. `cd /var/lib/mysql`
6. run `mysql -u root -p` and enter the password retrieved in step(3)
7. run `ALTER USER 'root'@'localhost' IDENTIFIED BY 'root_password';`
8. run `use mysql;`
9. run `CREATE USER 'flamingock_user'@'%' IDENTIFIED BY 'password';`
10. run `GRANT ALL PRIVILEGES ON *.* TO 'flamingock_user'@'%' WITH GRANT OPTION;`
11. run `FLUSH PRIVILEGES;`
12. run `create database flamingock;`
13. connect from client to localhost on 3306 with flamingock_user/password

# TODO
- Currently, there is no a way to know if the rollback should be executed
  - Potential solution: Add a method in the template to know if the rollback is provided
  - Interpret the rollback method's return. For example,providing an enum as response. Downside is that we may want to
    use the return value in the future for something...but it could be wrapped in a Result type.