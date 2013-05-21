/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.storage.Graph;
import java.util.TreeSet;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;

/**
 * A physical transit station with all its transit patterns. It has a node which
 * represents midnight and is used as entrance point. All the transit node are
 * connected to a exit/getOff node which can be used as end point of a route.
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
class TransitStop {

    private int transfereTime = 360;
    private Graph graph;
    private Stop stop;
    private int stopId;
    private int exitNodeId;
    private int defaultFlags;
    private int transitFlags;
    private int boardingFlags;
    private int alignFlags;
    private TreeSet<TransitNode> transitNodes = new TreeSet<TransitNode>();

    TransitStop(Graph graph, Stop stop) {
        this.graph = graph;
        this.stop = stop;
        this.stopId = getNewNodeId();
        this.exitNodeId = getNewNodeId();

        // Add getoff station node
        graph.setNode(exitNodeId, stop.getLat(), stop.getLon());

        // Add midnight node for station
        transitNodes.add(new TransitNode(stopId, 0));

        // Generate flags for time-expanded graph
        PublicTransitFlagEncoder encoder = new PublicTransitFlagEncoder();
        defaultFlags = encoder.flags(false);
        transitFlags = encoder.getTransitFlags(false);
        boardingFlags = encoder.getBoardingFlags(false);
        alignFlags = encoder.getAlignFlags(false);
    }

    /**
     * Generates the transit node for arrival and departure. A transit node
     * represents the waiting in a station.
     *
     * @param stopTime
     */
    public void addTransitNode(StopTime stopTime) {
        if (stopTime.isArrivalTimeSet()) {
            int time = stopTime.getArrivalTime() + transfereTime;
            transitNodes.add(new TransitNode(getNewNodeId(), time));
        }
        if (stopTime.isDepartureTimeSet()) {
            int time = stopTime.getArrivalTime();
            transitNodes.add(new TransitNode(getNewNodeId(), time));
        }
    }

    /**
     *
     * @return New node id
     */
    private int getNewNodeId() {
        return GTFSReader.getNewNodeId();
    }

    /**
     * Adds the transit nodes to the graph.
     */
    public void buildTransitNodes() {
        TransitNode previousNode = null;
        for (TransitNode transitNode : transitNodes) {
            graph.setNode(transitNode.getId(), stop.getLat(), stop.getLon());
            connect2ExitNode(transitNode.getId());
            if (previousNode != null) {
                graph.edge(previousNode.getId(), transitNode.getId(), transitNode.getTime() - previousNode.getTime(), transitFlags);
            }
            previousNode = transitNode;
        }
    }

    /**
     * Adds a departure node and connects the corresponding transit node.
     *
     * @param stopTime
     * @return id of new added node
     */
    public int addDepartureNode(StopTime stopTime) {
        int time = stopTime.getDepartureTime();
        TransitNode transitNode = transitNodes.floor(new TransitNode(graph.nodes(), time));
        int departureNodeId = getNewNodeId();

        // Add departure node to the graph
        graph.setNode(departureNodeId, stop.getLat(), stop.getLon());

        // Add edge from transit to the trip
        graph.edge(transitNode.getId(), departureNodeId, 0, boardingFlags);

        return departureNodeId;
    }

    /**
     * Adds a arrival node and connects the corresponding transit node.
     *
     * @param stopTime
     * @return id of new added node
     */
    public int addArrivalNode(StopTime stopTime) {
        int time = stopTime.getArrivalTime() + transfereTime;
        TransitNode transitNode = transitNodes.floor(new TransitNode(graph.nodes(), time));
        int arrivalNodeId = getNewNodeId();

        // Add arrival node to the graph
        graph.setNode(arrivalNodeId, stop.getLat(), stop.getLon());

        // Add edge from the trip to the tranist of the station
        graph.edge(arrivalNodeId, transitNode.getId(), transfereTime, alignFlags);

        return arrivalNodeId;
    }

    /**
     * Return the id of the node which represent the station at midnight
     *
     * @return node id
     */
    public int getStopId() {
        return stopId;
    }

    /**
     * Returns the exit node of the station. Should be used as endpoint on any
     * route.
     *
     * @return node id
     */
    public int getExitNodeId() {
        return exitNodeId;
    }

    /**
     * Connects a transit node to the exit node of the station
     *
     * @param nodeId
     */
    private void connect2ExitNode(int nodeId) {
        graph.edge(nodeId, exitNodeId, 0, defaultFlags);
    }

    private static class TransitNode implements Comparable<TransitNode> {

        private int time;
        private int id;

        public TransitNode(int id, int time) {
            this.id = id;
            this.time = time;

        }

        public int getId() {
            return id;
        }

        public int getTime() {
            return time;
        }

        @Override
        public int compareTo(TransitNode o) {
            if (o.time != this.time) {
                return Double.compare(this.time, o.time);
            } else {
                return Double.compare(this.id, o.id);
            }
        }
    }
}
