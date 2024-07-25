/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.examples.mysql.springboot;

import io.flamingock.springboot.v2.context.EnableFlamingock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


@EnableFlamingock
@SpringBootApplication
public class MysqlSpringbootApp {

    public static void main(String[] args) {
        SpringApplication.run(MysqlSpringbootApp.class, args);
    }

    @Bean
    public Connection mysqlConnection() throws ClassNotFoundException, SQLException {
        String myDriver = "com.mysql.cj.jdbc.Driver";
        String myUrl = "jdbc:mysql://localhost/flamingock";
        Class.forName(myDriver);
        return DriverManager.getConnection(myUrl, "flamingock_user", "password");
    }

}
