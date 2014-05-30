/*******************************************************************************
 * Copyright (c) 2014 itemis AG and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Matthias Wienand (itemis AG) - initial API and implementation
 * 
 *******************************************************************************/
package org.eclipse.gef4.fx.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.collections.MapChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;

import org.eclipse.gef4.fx.anchors.FXChopBoxAnchor;
import org.eclipse.gef4.fx.anchors.FXStaticAnchor;
import org.eclipse.gef4.fx.anchors.IFXAnchor;
import org.eclipse.gef4.geometry.euclidean.Angle;
import org.eclipse.gef4.geometry.euclidean.Vector;
import org.eclipse.gef4.geometry.planar.IGeometry;
import org.eclipse.gef4.geometry.planar.Point;

public abstract class AbstractFXConnection<T extends IGeometry> extends Group
		implements IFXConnection {

	// visuals
	private FXGeometryNode<T> curveNode = new FXGeometryNode<T>();
	private IFXDecoration startDecoration = null;
	private IFXDecoration endDecoration = null;

	private IFXAnchor startAnchor = null;
	private IFXAnchor endAnchor = null;
	private List<IFXAnchor> wayPointAnchors = new ArrayList<IFXAnchor>();

	private MapChangeListener<Node, Point> startPosCL = createStartPositionListener();
	private MapChangeListener<Node, Point> endPosCL = createEndPositionListener();
	private MapChangeListener<Node, Point> wayPosCL = createWayPositionListener();

	{
		// disable resizing children which would change their layout positions
		// in some cases
		setAutoSizeChildren(false);
	}

	@Override
	public void addWayPoint(int index, Point wayPoint) {
		addWayPointAnchor(index, new FXStaticAnchor(this, wayPoint));
	}

	@Override
	public void addWayPointAnchor(int index, IFXAnchor wayPointAnchor) {
		// check index out of range
		if (index < 0 || index > wayPointAnchors.size()) {
			throw new IllegalArgumentException("Index out of range (index: "
					+ index + ", size: " + wayPointAnchors.size() + ").");
		}

		// to keep it simple, we do not allow null anchors
		if (wayPointAnchor == null) {
			throw new IllegalArgumentException(
					"Way point anchor may not be null.");
		}

		// add new way point and register position listener
		wayPointAnchors.add(index, wayPointAnchor);
		wayPointAnchor.positionProperty().addListener(wayPosCL);

		// refresh connection
		refreshReferencePoints();
		refreshGeometry();
	}

	/**
	 * Arranges the given decoration according to the passed-in values. Returns
	 * the transformed end point of the arranged decoration.
	 * 
	 * @param deco
	 * @param start
	 * @param direction
	 * @param decoStart
	 * @param decoDirection
	 * @return the transformed end point of the arranged decoration
	 */
	private Point arrangeDecoration(IFXDecoration deco, Point start,
			Vector direction, Point decoStart, Vector decoDirection) {
		Node visual = deco.getVisual();

		// position
		Point2D posInParent = visual.localToParent(visual.sceneToLocal(start.x,
				start.y));
		visual.setLayoutX(posInParent.getX());
		visual.setLayoutY(posInParent.getY());

		// rotation
		Angle angleCW = null;
		if (!direction.isNull() && !decoDirection.isNull()) {
			angleCW = decoDirection.getAngleCW(direction);
			visual.getTransforms().clear();
			visual.getTransforms().add(new Rotate(angleCW.deg(), 0, 0));
		}

		// return corresponding curve point
		return angleCW == null ? start : start.getTranslated(decoDirection
				.getRotatedCW(angleCW).toPoint());
	}

	public abstract T computeGeometry(Point[] points);

	/**
	 * Returns a {@link Point} array containing reference points for the start
	 * and end anchors.
	 * 
	 * @return
	 */
	public Point[] computeReferencePoints() {
		// compute start/end point in local coordinate space
		Point start = getStartPoint();
		Point end = getEndPoint();

		// find reference points
		Point startReference = end;
		Point endReference = start;

		// first uncontained way point is start reference
		Node startNode = getStartAnchor().getAnchorageNode();
		if (startNode != null) {
			for (Point p : getWayPoints()) {
				Point2D local = startNode.sceneToLocal(localToScene(p.x, p.y));
				if (!startNode.contains(local)) {
					startReference = p;
					break;
				}
			}
		}

		// last uncontained way point is end reference
		Node endNode = getEndAnchor().getAnchorageNode();
		if (endNode != null) {
			for (Point p : getWayPoints()) {
				Point2D local = endNode.sceneToLocal(localToScene(p.x, p.y));
				if (!endNode.contains(local)) {
					endReference = p;
				}
			}
		}

		return new Point[] { startReference, endReference };
	}

	private MapChangeListener<Node, Point> createEndPositionListener() {
		return new MapChangeListener<Node, Point>() {
			@Override
			public void onChanged(
					javafx.collections.MapChangeListener.Change<? extends Node, ? extends Point> change) {
				Node anchored = change.getKey();
				if (anchored == AbstractFXConnection.this) {
					Point[] referencePoints = computeReferencePoints();
					updateStartReferencePoint(referencePoints[0]);
					refreshGeometry();
				}
			}
		};
	}

	private MapChangeListener<Node, Point> createStartPositionListener() {
		return new MapChangeListener<Node, Point>() {
			@Override
			public void onChanged(
					javafx.collections.MapChangeListener.Change<? extends Node, ? extends Point> change) {
				Node anchored = change.getKey();
				if (anchored == AbstractFXConnection.this) {
					Point[] referencePoints = computeReferencePoints();
					updateEndReferencePoint(referencePoints[1]);
					refreshGeometry();
				}
			}
		};
	}

	private MapChangeListener<Node, Point> createWayPositionListener() {
		return new MapChangeListener<Node, Point>() {
			@Override
			public void onChanged(
					javafx.collections.MapChangeListener.Change<? extends Node, ? extends Point> change) {
				Node anchored = change.getKey();
				if (anchored == AbstractFXConnection.this) {
					refreshReferencePoints();
					refreshGeometry();
				}
			}
		};
	}

	/**
	 * Returns the end point for computing this connection's curve visual.
	 * 
	 * @return the end point for computing this connection's curve visual
	 */
	private Point getCurveEndPoint() {
		if (endDecoration == null) {
			return getEndPoint();
		}

		Point sp = getEndPoint();
		Point next = wayPointAnchors.size() > 0 ? wayPointAnchors.get(
				wayPointAnchors.size() - 1).getPosition(this) : getStartPoint();
		Vector sv = new Vector(sp, next);

		Point dsp = endDecoration.getLocalStartPoint();
		Point dep = endDecoration.getLocalEndPoint();
		Vector dv = new Vector(dsp, dep);

		// TODO: move arrangement to somewhere else
		return arrangeDecoration(endDecoration, sp, sv, dsp, dv);
	}

	@Override
	public FXGeometryNode<T> getCurveNode() {
		return curveNode;
	}

	/**
	 * <p>
	 * Returns all points of this connection which are relevant for computing
	 * the curveNode, which are:
	 * <ol>
	 * <li>curve start point: computed using start anchor, start decoration, and
	 * first way point (or end anchor)</li>
	 * <li>way points</li>
	 * <li>curve end point: computed using end anchor, end decoration, and last
	 * way point (or start anchor)</li>
	 * </ol>
	 * </p>
	 * <p>
	 * In case an assigned anchor returns <code>null</code> as the position for
	 * this curve (i.e. the anchor is not fully set-up and therefore did not yet
	 * compute the position) this method returns an empty array.
	 * </p>
	 * 
	 * @return all curve relevant points
	 */
	public Point[] getCurvePoints() {
		Point[] points = new Point[wayPointAnchors.size() + 2];

		points[0] = getCurveStartPoint();
		if (points[0] == null) {
			return new Point[] {};
		}

		for (int i = 0; i < wayPointAnchors.size(); i++) {
			points[1 + i] = wayPointAnchors.get(i).getPosition(this);
			if (points[i + 1] == null) {
				return new Point[] {};
			}
		}

		points[points.length - 1] = getCurveEndPoint();
		if (points[points.length - 1] == null) {
			return new Point[] {};
		}

		return points;
	}

	/**
	 * Returns the start point for computing this connection's curve visual.
	 * 
	 * @return the start point for computing this connection's curve visual
	 */
	private Point getCurveStartPoint() {
		if (startDecoration == null) {
			return getStartPoint();
		}

		Point sp = getStartPoint();
		Point next = wayPointAnchors.size() > 0 ? wayPointAnchors.get(0)
				.getPosition(this) : getEndPoint();
		Vector sv = new Vector(sp, next);

		Point dsp = startDecoration.getLocalStartPoint();
		Point dep = startDecoration.getLocalEndPoint();
		Vector dv = new Vector(dsp, dep);

		// TODO: move arrangement to somewhere else
		return arrangeDecoration(startDecoration, sp, sv, dsp, dv);
	}

	@Override
	public IFXAnchor getEndAnchor() {
		if (endAnchor == null) {
			endAnchor = new FXStaticAnchor(this, new Point());
		}
		return endAnchor;
	}

	@Override
	public IFXDecoration getEndDecoration() {
		return endDecoration;
	}

	@Override
	public Point getEndPoint() {
		return getEndAnchor().getPosition(this);
	}

	@Override
	public Point[] getPoints() {
		List<Point> wayPoints = getWayPoints();
		Point[] points = new Point[wayPoints.size() + 2];

		points[0] = getStartPoint();
		int i = 1;
		for (Point wp : wayPoints) {
			points[i++] = wp;
		}
		points[points.length - 1] = getEndPoint();

		return points;
	}

	@Override
	public IFXAnchor getStartAnchor() {
		if (startAnchor == null) {
			startAnchor = new FXStaticAnchor(this, new Point());
		}
		return startAnchor;
	}

	@Override
	public IFXDecoration getStartDecoration() {
		return startDecoration;
	}

	@Override
	public Point getStartPoint() {
		return getStartAnchor().getPosition(this);
	}

	@Override
	public Point getWayPoint(int index) {
		return wayPointAnchors.get(index).getPosition(this);
	}

	@Override
	public List<IFXAnchor> getWayPointAnchors() {
		return Collections.unmodifiableList(wayPointAnchors);
	}

	@Override
	public List<Point> getWayPoints() {
		List<Point> wayPoints = new ArrayList<Point>(wayPointAnchors.size());
		for (int i = 0; i < wayPointAnchors.size(); i++) {
			wayPoints.add(wayPointAnchors.get(i).getPosition(this));
		}
		return wayPoints;
	}

	protected void refreshGeometry() {
		// clear current visuals
		getChildren().clear();

		// compute new curve
		curveNode.setGeometry(computeGeometry(getCurvePoints()));

		// z-order decorations above curve
		getChildren().add(curveNode);
		if (startDecoration != null) {
			getChildren().add(startDecoration.getVisual());
		}
		if (endDecoration != null) {
			getChildren().add(endDecoration.getVisual());
		}

		// FIXME: #432035 rotation of decorations is slightly off right now
	}

	/**
	 * Updates the start and end anchor reference points after computing them
	 * using {@link #computeReferencePoints()}.
	 */
	protected void refreshReferencePoints() {
		Point[] referencePoints = computeReferencePoints();
		updateStartReferencePoint(referencePoints[0]);
		updateEndReferencePoint(referencePoints[1]);
	}

	@Override
	public void removeAllWayPoints() {
		for (int i = wayPointAnchors.size() - 1; i >= 0; i--) {
			removeWayPoint(i);
		}
	}

	@Override
	public void removeWayPoint(int index) {
		// check index out of range
		if (index < 0 || index >= wayPointAnchors.size()) {
			throw new IllegalArgumentException("Index out of range (index: "
					+ index + ", size: " + wayPointAnchors.size() + ").");
		}

		// remove anchor and listener
		IFXAnchor anchor = wayPointAnchors.get(index);
		anchor.positionProperty().removeListener(wayPosCL);
		wayPointAnchors.remove(index);

		// refresh connection
		refreshReferencePoints();
		refreshGeometry();
	}

	@Override
	public void setEndAnchor(IFXAnchor endAnchor) {
		// to keep it simple, we do not allow null anchors
		if (endAnchor == null) {
			throw new IllegalArgumentException("End anchor may not be null.");
		}

		// unregister old listener if we had an end anchor assigned previously
		if (this.endAnchor != null) {
			this.endAnchor.positionProperty().removeListener(endPosCL);
		}

		// assign new end anchor and register listener
		this.endAnchor = endAnchor;
		endAnchor.positionProperty().addListener(endPosCL);

		// refresh connection
		refreshReferencePoints();
		refreshGeometry();
	}

	@Override
	public void setEndDecoration(IFXDecoration endDeco) {
		endDecoration = endDeco;
		refreshGeometry();
	}

	@Override
	public void setEndPoint(Point endPoint) {
		setEndAnchor(new FXStaticAnchor(this, endPoint));
	}

	@Override
	public void setStartAnchor(IFXAnchor startAnchor) {
		// to keep it simple, we do not allow null anchors
		if (startAnchor == null) {
			throw new IllegalArgumentException("Start anchor may not be null.");
		}

		// unregister listener if we already had a start anchor assigned
		if (this.startAnchor != null) {
			this.startAnchor.positionProperty().removeListener(startPosCL);
		}

		// assign new start anchor and register listener
		this.startAnchor = startAnchor;
		startAnchor.positionProperty().addListener(startPosCL);

		// refresh connection
		refreshReferencePoints();
		refreshGeometry();
	}

	@Override
	public void setStartDecoration(IFXDecoration startDeco) {
		startDecoration = startDeco;
		refreshGeometry();
	}

	@Override
	public void setStartPoint(Point startPoint) {
		setStartAnchor(new FXStaticAnchor(this, startPoint));
	}

	@Override
	public void setWayPoint(int index, Point wayPoint) {
		setWayPointAnchor(index, new FXStaticAnchor(this, wayPoint));
	}

	@Override
	public void setWayPointAnchor(int index, IFXAnchor wayPointAnchor) {
		// check index out of range
		if (index < 0 || index >= wayPointAnchors.size()) {
			throw new IllegalArgumentException(
					"The specified way point anchor does not exist. (index: "
							+ index + ", size: " + wayPointAnchors.size() + ")");
		}

		// to keep it simple, we do not allow null anchors
		if (wayPointAnchor == null) {
			throw new IllegalArgumentException(
					"Way point anchor may not be null.");
		}

		// unregister listener from previous anchor
		wayPointAnchors.get(index).positionProperty().removeListener(wayPosCL);

		// assign new anchor and register listener
		wayPointAnchors.set(index, wayPointAnchor);
		wayPointAnchor.positionProperty().addListener(wayPosCL);

		// refresh connection
		refreshReferencePoints();
		refreshGeometry();
	}

	@Override
	public void setWayPoints(List<Point> wayPoints) {
		removeAllWayPoints();
		for (Point wp : wayPoints) {
			addWayPoint(wayPointAnchors.size(), wp);
		}
	}

	private void updateEndReferencePoint(Point ref) {
		if (getEndAnchor() instanceof FXChopBoxAnchor) {
			if (ref != null) {
				FXChopBoxAnchor anchor = (FXChopBoxAnchor) getEndAnchor();
				if (!ref.equals(anchor.getReferencePoint(this))) {
					anchor.setReferencePoint(this, ref);
				}
			}
		}
	}

	private void updateStartReferencePoint(Point ref) {
		if (getStartAnchor() instanceof FXChopBoxAnchor) {
			if (ref != null) {
				FXChopBoxAnchor anchor = (FXChopBoxAnchor) getStartAnchor();
				if (!ref.equals(anchor.getReferencePoint(this))) {
					anchor.setReferencePoint(this, ref);
				}
			}
		}
	}

}
