/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.internal.util.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flamingock.internal.util.JsonObjectMapper;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.ServerException;
import io.flamingock.internal.util.FlamingockError;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static io.flamingock.internal.util.FlamingockError.GENERIC_ERROR;
import static io.flamingock.internal.util.FlamingockError.HTTP_CONNECTION_ERROR;
import static io.flamingock.internal.util.FlamingockError.OBJECT_MAPPING_ERROR;

public final class Http {

    private static final String RUNNER_ID_HEADER_NAME = "flamingock-runner-id";
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    private enum Method {
        GET, POST, PUT, DELETE
    }


    public static final RequestBuilderFactory DEFAULT_INSTANCE = Http.builderFactory(HttpClients.createDefault(), JsonObjectMapper.DEFAULT_INSTANCE);

    public static RequestBuilderFactory builderFactory(CloseableHttpClient client,
                                                       ObjectMapper objectMapper) {
        return new RequestBuilderFactory(client, objectMapper);
    }

    private Http() {
    }


    public static class RequestBuilderFactory implements AutoCloseable {

        private final CloseableHttpClient client;

        private final ObjectMapper objectMapper;

        private RequestBuilderFactory(CloseableHttpClient client, ObjectMapper objectMapper) {
            this.client = client;
            this.objectMapper = objectMapper;
        }

        public RequestBuilder getRequestBuilder(String host) {
            return new RequestBuilder(host, client, objectMapper);
        }

        @Override
        public void close() throws IOException {
            if(client != null) {
                client.close();
            }
        }
    }

    public static class RequestBuilder {
        private final CloseableHttpClient client;

        private final ObjectMapper objectMapper;

        private final Map<String, String> sharedHeaders = new HashMap<>();

        private final String host;


        private RequestBuilder(String host,
                               CloseableHttpClient client,
                               ObjectMapper objectMapper) {
            this.host = host;
            this.client = client;
            this.objectMapper = objectMapper;
        }

        public RequestWithBody POST(String pathTemplate) {
            return newRequestWithBody(Method.POST, pathTemplate);
        }

        public Request GET(String pathTemplate) {
            return newRequest(Method.POST, pathTemplate);
        }

        public RequestWithBody PUT(String pathTemplate) {
            return newRequestWithBody(Method.PUT, pathTemplate);
        }

        public Request DELETE(String pathTemplate) {
            return newRequest(Method.DELETE, pathTemplate);
        }

        private Request newRequest(Method method, String pathTemplate) {
            return new Request(host, pathTemplate, method, client, objectMapper, new HashMap<>(sharedHeaders));
        }

        private RequestWithBody newRequestWithBody(Method method, String pathTemplate) {
            return new RequestWithBody(host, pathTemplate, method, client, objectMapper, new HashMap<>(sharedHeaders));
        }
    }

    public static abstract class AbstractRequest<SELF extends AbstractRequest<SELF>> {


        protected static final Logger logger = LoggerFactory.getLogger(Http.class);

        protected final CloseableHttpClient client;

        protected final ObjectMapper objectMapper;

        protected final String host;

        protected final String pathTemplate;

        protected final Map<String, String> headers;

        protected final Map<String, Object> pathParameters;

        protected final Map<String, Object> queryParameters;

        protected final Method method;

        protected boolean json = true;

        protected AbstractRequest(String host,
                                  String pathTemplate,
                                  Method method,
                                  CloseableHttpClient client,
                                  ObjectMapper objectMapper,
                                  Map<String, String> headers) {
            this.host = host;
            this.pathTemplate = pathTemplate;
            this.method = method;
            this.client = client;
            this.objectMapper = objectMapper;
            this.headers = headers;
            this.pathParameters = new HashMap<>();
            this.queryParameters = new HashMap<>();
        }

        public SELF notJson() {
            json = false;
            return getInstance();
        }

        public SELF addPathParameter(String paramName, Object paramValue) {
            pathParameters.put(paramName, paramValue);
            return getInstance();
        }

        public SELF addQueryParameter(String paramName, Object paramValue) {
            queryParameters.put(paramName, paramValue);
            return getInstance();
        }

        public SELF addHeader(String headerName, String value) {
            headers.put(headerName, value);
            return getInstance();
        }

        public SELF withBearerToken(String token) {
            headers.put(AUTHORIZATION_HEADER_NAME, "Bearer "+ token);
            return getInstance();
        }

        public SELF withRunnerId(RunnerId runnerId) {
            addHeader(RUNNER_ID_HEADER_NAME, runnerId.toString());
            return getInstance();
        }

        public String getRequestString() {
            return String.format("%s %s", method.toString(), getFinalUrl());
        }

        public void execute() {
            executeInternal(null);
        }

        public <T> T execute(Class<T> type) {
            if (type == null) {
                throw new RuntimeException("Response type expected not to be null");
            }
            return executeInternal(type);
        }

        private <T> T executeInternal(Class<T> type) {
            if (json) {
                headers.put("Content-Type", "application/json");
            }
            String url = getFinalUrl();
            HttpRequestBase request = getRequest(url);
            setHeaders(request);
            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    if (type != null && response.getEntity() != null) {
                        try {
                            return mapResult(response.getEntity(), type);

                        } catch (IOException ex) {
                            throw new ServerException(
                                    request.toString(),
                                    getBodyIfPresent(),
                                    new HttpFlamingockError(statusCode,
                                            OBJECT_MAPPING_ERROR,
                                            String.format("Http connection OK. Error mapping to[%s] the response:\n %s",
                                                    type.getSimpleName(),
                                                    response.getEntity().toString()
                                            )));
                        }
                    } else {
                        return null;
                    }

                } else {
                    if (response.getEntity() != null) {
                        FlamingockError error;
                        try {
                            error = mapResult(response.getEntity(), FlamingockError.class);

                        } catch (Throwable ex) {
                            error = new FlamingockError(GENERIC_ERROR, false, response.getEntity().toString());
                        }

                        throw new ServerException(request.toString(), getBodyIfPresent(), new HttpFlamingockError(statusCode, error));
                    } else {
                        throw new ServerException(request.toString(), getBodyIfPresent(), new HttpFlamingockError(statusCode, GENERIC_ERROR, "No error info returned"));
                    }
                }

            } catch (ServerException e) {
              throw e;
            } catch (IOException e) {
                throw new ServerException(request.toString(), getBodyIfPresent(), new FlamingockError(HTTP_CONNECTION_ERROR, false, e.getMessage()));
            } catch (Throwable e) {
                throw new ServerException(request.toString(), getBodyIfPresent(), new FlamingockError(GENERIC_ERROR, false, e.getMessage()));
            }
        }

        private String getBodyIfPresent() {
            if (this instanceof RequestWithBody) {
                RequestWithBody request = (RequestWithBody) this;
                return request.body != null ? request.body.toString() : null;
            } else {
                return null;
            }
        }


        private <T> T mapResult(HttpEntity responseEntity, Class<T> type) throws IOException {
            T mappedBody;
            String responseBody = EntityUtils.toString(responseEntity);
            logger.trace("Response Status Code: {}", responseBody);
            mappedBody = objectMapper.readValue(responseBody, type);
            return mappedBody;
        }

        private String getFinalUrl() {
            try {
                String path = pathTemplate;
                for (Map.Entry<String, Object> entry : pathParameters.entrySet()) {
                    path = path.replace("{" + entry.getKey() + "}", entry.getValue().toString());
                }

                URIBuilder uriBuilder = new URIBuilder(host)
                        .setPath(path);
                queryParameters.entrySet()
                        .stream()
                        .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                        .forEach(entry -> uriBuilder.addParameter(entry.getKey(), entry.getValue().toString()));
                return uriBuilder.toString();

            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

        }

        protected HttpRequestBase getRequest(String url) {
            switch (method) {
                case GET:
                    return new HttpGet(url);
                case POST:
                    return new HttpPost(url);
                case PUT:
                    return new HttpPut(url);
                case DELETE:
                    return new HttpDelete(url);
                default:
                    throw new RuntimeException("Not found http method: " + method);
            }
        }

        private void setHeaders(HttpRequestBase request) {
            headers.forEach(request::setHeader);
        }

        private static boolean isSuccessfull(org.apache.http.HttpResponse response) {
            int statusCode = response.getStatusLine().getStatusCode();
            logger.trace("Response Status Code: {}", statusCode);
            return statusCode >= 200 && statusCode < 300;
        }

        protected abstract SELF getInstance();
    }

    public static class Request extends AbstractRequest<Request> {

        protected Request(String host,
                          String pathTemplate,
                          Method method,
                          CloseableHttpClient client,
                          ObjectMapper objectMapper,
                          Map<String, String> headers) {
            super(host, pathTemplate, method, client, objectMapper, headers);
        }

        @Override
        protected Request getInstance() {
            return this;
        }
    }

    public static class RequestWithBody extends AbstractRequest<RequestWithBody> {


        private Object body;

        private RequestWithBody(String host,
                                String pathTemplate,
                                Method method,
                                CloseableHttpClient client,
                                ObjectMapper objectMapper,
                                Map<String, String> headers) {
            super(host, pathTemplate, method, client, objectMapper, headers);
        }

        public RequestWithBody setBody(Object body) {
            this.body = body;
            return this;
        }

        @Override
        public String getRequestString() {
            try {
                return super.getRequestString() + "\n" + objectMapper.writeValueAsString(body);
            } catch (JsonProcessingException e) {
                logger.warn(e.getMessage(), e);
                return super.getRequestString();
            }
        }

        protected HttpEntityEnclosingRequestBase getRequest(String url) {
            HttpEntityEnclosingRequestBase request;
            switch (method) {
                case POST:
                    request = new HttpPost(url);
                    break;
                case PUT:
                    request = new HttpPut(url);
                    break;
                default:
                    throw new RuntimeException("Not found http method: " + method);
            }
            addRequestBody(request);
            return request;
        }

        @Override
        protected RequestWithBody getInstance() {
            return this;
        }


        private void addRequestBody(HttpEntityEnclosingRequestBase request) {
            if (body == null) {
                throw new RuntimeException(String.format("%s request requires non-null body", method));
            }
            try {
                request.setEntity(new StringEntity(objectMapper.writeValueAsString(body)));

            } catch (UnsupportedEncodingException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

    }

}