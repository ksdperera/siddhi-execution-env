/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.extension.siddhi.execution.env;

import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.annotation.Parameter;
import org.wso2.siddhi.annotation.util.DataType;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.event.stream.populater.ComplexEventPopulater;
import org.wso2.siddhi.core.executor.ConstantExpressionExecutor;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.query.processor.Processor;
import org.wso2.siddhi.core.query.processor.stream.StreamProcessor;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.exception.SiddhiAppValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Siddhi Resource Identify Stream Processor Extension to register the resources with name and serve registered
 * resource count for given name.
 */
@Extension(
        name = "resourceIdentifier",
        namespace = "env",
        description = "The resource identify stream processor registering the resource name with reference " +
                "in static map. And serve static resources count for specific resource name.",
        parameters = {
                @Parameter(name = "resource.group.id",
                        description = "The resource group name.",
                        type = {DataType.STRING})
        },
        examples = {
                @Example(
                        syntax =
                                "@info(name='product_color_code_rule') \n" +
                                        "from SweetProductDefectsDetector#env:resourceIdentifier(\"rule-group-1\")\n" +
                                        "select productId, if(colorCode == '#FF0000', true, false) as isValid\n" +
                                        "insert into DefectDetectionResult;\n" +
                                        "\n" +
                                        "@info(name='product_dimensions_rule') \n" +
                                        "from SweetProductDefectsDetector#env:resourceIdentifier(\"rule-group-1\")\n" +
                                        "select productId, if(height == 5 && width ==10, true, false) as isValid\n" +
                                        "insert into DefectDetectionResult;\n" +
                                        "@info(name='defect_analyzer') \n" +
                                        "from DefectDetectionResult#window.env:resourceBatch(\"rule-group-1\", " +
                                        "productId, 60000)\n" +
                                        "select productId, and(not isValid) as isDefected\n" +
                                        "insert into SweetProductDefectAlert;",
                        description = "These are two rule base queries, which processing the same events from " +
                                "the SweetProductDefectsDetector and output the process results into same stream " +
                                "DefectDetectionResult. Also, the queries like this can be newly introduce into " +
                                "Siddhi Application and the number of output events(in DefectDetectionResult) " +
                                "depends on the number of available queries. If we need to further aggregate " +
                                "results for particular correlation.id: productId from the DefectDetectionResult" +
                                " stream, follow-up queries should wait for events with same correlation.id from " +
                                "all these available queries. For that future queries should know the number of " +
                                "events which can expect from these 'rule' base queries for given correlation id." +
                                "To address this requirement, in above example, we have defined the resource " +
                                "identifier with 'resource.group.id: rule-group-1' in both the 'rule' queries, so " +
                                "that the other extensions can be used the number of registered " +
                                "resource 'rule-group-1' count for their internal processing. Here the " +
                                "'defect_analyzer' query has env:resourceBatch window where it uses registered " +
                                "resource 'rule-group-1' count to determine the event waiting condition for events " +
                                "from DefectDetectionResult stream."
                )
        }
)
public class ResourceIdentifierStreamProcessor extends StreamProcessor {
    private static Map<String, List<ResourceIdentifierStreamProcessor>> resourceIdentifyStreamProcessorMap =
            new ConcurrentHashMap<>();
    private String resourceName;

    @Override
    protected void process(ComplexEventChunk<StreamEvent> complexEventChunk, Processor processor,
                           StreamEventCloner streamEventCloner, ComplexEventPopulater complexEventPopulater) {
        nextProcessor.process(complexEventChunk);
    }

    @Override
    protected List<Attribute> init(AbstractDefinition abstractDefinition, ExpressionExecutor[] expressionExecutors,
                                   ConfigReader configReader, SiddhiAppContext siddhiAppContext) {
        int inputExecutorLength = attributeExpressionExecutors.length;
        if (inputExecutorLength == 1) {
            if (attributeExpressionExecutors[0] instanceof ConstantExpressionExecutor) {
                if (attributeExpressionExecutors[0].getReturnType() == Attribute.Type.STRING) {
                    resourceName = (String) ((ConstantExpressionExecutor) attributeExpressionExecutors[0]).getValue();
                } else {
                    throw new SiddhiAppValidationException("Resource Identify Stream Processor first parameter " +
                            "attribute should be string type but found " +
                            attributeExpressionExecutors[0].getReturnType());
                }
            } else {
                throw new SiddhiAppValidationException("Resource Identify Stream Processor should have " +
                        "constant parameter attributes but found a dynamic attribute " +
                        attributeExpressionExecutors[0].getClass().getCanonicalName());
            }
        } else {
            throw new SiddhiAppValidationException("Resource Identify Stream Processor should only have one " +
                    "parameter (<string> resource.name), but found " + attributeExpressionExecutors.length +
                    "input attributes");
        }
        return new ArrayList<Attribute>();
    }

    @Override
    public void start() {
        if (resourceName != null) {
            List<ResourceIdentifierStreamProcessor> resourceIdentifierStreamProcessorList =
                    resourceIdentifyStreamProcessorMap.get(resourceName);
            if (resourceIdentifierStreamProcessorList != null) {
                resourceIdentifierStreamProcessorList.add(this);
            } else {
                List<ResourceIdentifierStreamProcessor> list = new ArrayList<>();
                list.add(this);
                resourceIdentifyStreamProcessorMap.put(resourceName, list);
            }
        }
    }

    @Override
    public void stop() {
        if (resourceName != null) {
            List<ResourceIdentifierStreamProcessor> resourceIdentifierStreamProcessorList =
                    resourceIdentifyStreamProcessorMap.get(resourceName);
            if (resourceIdentifierStreamProcessorList != null) {
                resourceIdentifierStreamProcessorList.remove(this);
                if (resourceIdentifierStreamProcessorList.size() == 0) {
                    resourceIdentifyStreamProcessorMap.remove(resourceName);
                }
            }
        }
    }

    @Override
    public Map<String, Object> currentState() {
        return null;
    }

    @Override
    public void restoreState(Map<String, Object> map) {

    }

    public static int getResourceCount(String resourceName) {
        List<ResourceIdentifierStreamProcessor> resourceIdentifierStreamProcessorList =
                resourceIdentifyStreamProcessorMap.get(resourceName);
        if (resourceIdentifierStreamProcessorList != null) {
            return resourceIdentifierStreamProcessorList.size();
        } else {
            return 0;
        }
    }
}
