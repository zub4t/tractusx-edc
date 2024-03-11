/********************************************************************************
 * Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.dataplane.proxy.provider.api;

import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.http.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.tractusx.edc.dataplane.proxy.provider.api.gateway.ProviderGatewayController;
import org.eclipse.tractusx.edc.dataplane.proxy.provider.api.validation.ProxyProviderDataAddressResolver;
import org.eclipse.tractusx.edc.dataplane.proxy.spi.provider.gateway.authorization.AuthorizationHandlerRegistry;
import org.eclipse.tractusx.edc.dataplane.proxy.spi.provider.gateway.configuration.GatewayConfigurationRegistry;

import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 * Adds the consumer proxy data plane API.
 */
@Extension(value = DataPlaneProxyProviderApiExtension.NAME)
public class DataPlaneProxyProviderApiExtension implements ServiceExtension {
    public static final int DEFAULT_THREAD_POOL = 10;
    static final String NAME = "Data Plane Proxy Provider API";
    @Setting(value = "Thread pool size for the provider data plane proxy gateway", type = "int")
    private static final String THREAD_POOL_SIZE = "tx.dpf.provider.proxy.thread.pool";

    @Setting(value = "Path to register the ProviderGatewayController to", type = "String")
    private static final String WEB_HTTP_GATEWAY_PATH_SETTING = "web.http.gateway.path";

    @Setting(value = "Port to register the ProviderGatewayController to", type = "int")
    private static final String WEB_HTTP_GATEWAY_PORT_SETTING = "web.http.gateway.port";

    private static final String GATEWAY_CONTEXT = "gateway";

    @Setting
    private static final String CONTROL_PLANE_VALIDATION_ENDPOINT = "edc.dataplane.token.validation.endpoint";

    @Inject
    private WebService webService;

    @Inject
    private PipelineService pipelineService;

    @Inject
    private Monitor monitor;

    @Inject
    private GatewayConfigurationRegistry configurationRegistry;

    @Inject
    private AuthorizationHandlerRegistry authorizationRegistry;

    @Inject
    private TypeManager typeManager;

    @Inject
    private EdcHttpClient httpClient;

    private ExecutorService executorService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        executorService = newFixedThreadPool(context.getConfig().getInteger(THREAD_POOL_SIZE, DEFAULT_THREAD_POOL));

        var validationEndpoint = context.getConfig().getString(CONTROL_PLANE_VALIDATION_ENDPOINT);

        var dataAddressResolver = new ProxyProviderDataAddressResolver(httpClient, validationEndpoint, typeManager.getMapper());

        var controller = new ProviderGatewayController(pipelineService,
                dataAddressResolver,
                configurationRegistry,
                authorizationRegistry,
                executorService,
                monitor);

        // If a setting for the port mapping for a separate gateway context exists, we assume the context also exists and register into that
        // Otherwise we use the default context
        if (context.getConfig().hasKey(WEB_HTTP_GATEWAY_PATH_SETTING) && context.getConfig().hasKey(WEB_HTTP_GATEWAY_PORT_SETTING)) {
            webService.registerResource(GATEWAY_CONTEXT, controller);
        } else {
            webService.registerResource(controller);
        }
    }


    @Override
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

}