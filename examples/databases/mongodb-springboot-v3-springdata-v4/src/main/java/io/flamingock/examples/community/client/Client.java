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

package io.flamingock.examples.community.client;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import io.flamingock.examples.community.CommunitySpringbootMongodbSpringdataApp;

import java.time.LocalDateTime;
import java.util.Objects;

@Document(collection = CommunitySpringbootMongodbSpringdataApp.CLIENTS_COLLECTION_NAME)
@CompoundIndexes({
    @CompoundIndex(def = "{'name':1, 'deleted':1}", name = "user_name_idx"),
    @CompoundIndex(def = "{'email':1, 'deleted':1}", name = "user_email_idx"),
    @CompoundIndex(def = "{'phone':1, 'deleted':1}", name = "user_phone_idx"),
    @CompoundIndex(def = "{'country':1, 'deleted':1, 'activation.status':1}", name = "user_country_activation_idx")
})
public class Client {

  @Id
  private String id;

  @Field
  private LocalDateTime dateTime;

  @Field("name")
  private String name;

  @Field("email")
  private String email;

  @Field("phone")
  private String phone;

  @Field("country")
  private String country;

  @Field("deleted")
  private boolean deleted;
  
  @Field("counter")
  private int counter;

  public Client() {
    this.dateTime = LocalDateTime.now();
  }


  public Client(String name, String email, String phone, String country) {
    this();
    this.name = name;
    this.email = email;
    this.phone = phone;
    this.country = country;
    this.deleted = false;
    this.counter = 0;
  }

  // setters returning 'this' for fluent use in stream. Shouldn't be taken as precedent
  public Client setId(String id) {
    this.id = id;
    return this;
  }

  public Client setName(String name) {
    this.name = name;
    return this;
  }

  public Client setEmail(String email) {
    this.email = email;
    return this;
  }

  public Client setPhone(String phone) {
    this.phone = phone;
    return this;
  }

  public Client setCountry(String country) {
    this.country = country;
    return this;
  }

  public Client setDeleted(boolean deleted) {
    this.deleted = deleted;
    return this;
  }

  public Client setDateTime(LocalDateTime dateTime) {
    this.dateTime = dateTime;
    return this;
  }
  
  public Client setCounter(int counter) {
    this.counter = counter;
    return this;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getCountry() {
    return country;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public LocalDateTime getDateTime() {
    return dateTime;
  }
  
  public int getCounter() {
    return counter;
  }


  @Override
  public String toString() {
    return "Client{" +
            "name='" + name + '\'' +
            '}';
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Client client = (Client) o;
    return deleted == client.deleted &&
            counter == client.counter &&
            Objects.equals(id, client.id) &&
            Objects.equals(dateTime, client.dateTime) &&
            Objects.equals(name, client.name) &&
            Objects.equals(email, client.email) &&
            Objects.equals(phone, client.phone) &&
            Objects.equals(country, client.country);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, dateTime, name, email, phone, country, deleted, counter);
  }
}
