package io.github.zancrow321.minecraftygo.tools.bbmodel;

import java.util.List;
import java.util.Map;

/**
 * Axis-aligned bounds of a converted model in Blockbench coordinates (pixels), with the idle pose applied. Rotations
 * use Blockbench's Euler order (ZYX: X is applied first); animation values are converted from the Bedrock
 * convention back to Blockbench space.
 */
public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
	public double width() {
		return maxX - minX;
	}

	public double height() {
		return maxY - minY;
	}

	public double depth() {
		return maxZ - minZ;
	}

	public static Bounds of(List<GeoConverter.Bone> bones, AnimationConverter.Pose pose) {
		Map<String, GeoConverter.Bone> byName = GeoConverter.byName(bones);
		double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
		double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		for (GeoConverter.Bone bone : bones) {
			for (BbModel.Cube cube : bone.cubes()) {
				double i = cube.inflate();
				double[] from = {cube.from()[0] - i, cube.from()[1] - i, cube.from()[2] - i};
				double[] to = {cube.to()[0] + i, cube.to()[1] + i, cube.to()[2] + i};
				for (int corner = 0; corner < 8; corner++) {
					double[] p = {
							(corner & 1) == 0 ? from[0] : to[0],
							(corner & 2) == 0 ? from[1] : to[1],
							(corner & 4) == 0 ? from[2] : to[2]};
					p = rotateAround(p, cube.origin(), cube.rotation());
					for (GeoConverter.Bone b = bone; b != null; b = b.parent() == null ? null : byName.get(b.parent())) {
						double[] animRotation = pose.rotations().getOrDefault(b.name(), new double[3]);
						double[] animPosition = pose.positions().getOrDefault(b.name(), new double[3]);
						double[] rotation = {
								b.rotation()[0] - animRotation[0],
								b.rotation()[1] - animRotation[1],
								b.rotation()[2] + animRotation[2]};
						p = rotateAround(p, b.pivot(), rotation);
						p[0] -= animPosition[0];
						p[1] += animPosition[1];
						p[2] += animPosition[2];
					}
					for (int axis = 0; axis < 3; axis++) {
						min[axis] = Math.min(min[axis], p[axis]);
						max[axis] = Math.max(max[axis], p[axis]);
					}
				}
			}
		}
		if (min[0] == Double.POSITIVE_INFINITY) {
			return new Bounds(0, 0, 0, 0, 0, 0);
		}
		return new Bounds(min[0], min[1], min[2], max[0], max[1], max[2]);
	}

	static double[] rotateAround(double[] point, double[] pivot, double[] degrees) {
		if (GeoConverter.isZero(degrees)) {
			return point.clone();
		}
		double x = point[0] - pivot[0];
		double y = point[1] - pivot[1];
		double z = point[2] - pivot[2];
		double rx = Math.toRadians(degrees[0]);
		double ry = Math.toRadians(degrees[1]);
		double rz = Math.toRadians(degrees[2]);
		// X
		double y1 = y * Math.cos(rx) - z * Math.sin(rx);
		double z1 = y * Math.sin(rx) + z * Math.cos(rx);
		// Y
		double x2 = x * Math.cos(ry) + z1 * Math.sin(ry);
		double z2 = -x * Math.sin(ry) + z1 * Math.cos(ry);
		// Z
		double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz);
		double y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
		return new double[] {x3 + pivot[0], y3 + pivot[1], z2 + pivot[2]};
	}
}
