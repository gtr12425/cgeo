package cgeo.geocaching.models.geoitem;

import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Represents a drawable primitive GeoItem such as a point, polyline or polygon */
public class GeoPrimitive implements GeoItem, Parcelable {

    //immutable
    @NonNull private final GeoItem.GeoType type;
    @NonNull private final List<Geopoint> points;
    @Nullable private final GeoIcon icon;
    private final float radius;
    @Nullable private final GeoStyle style;

    private final int zLevel;

    //lazy-calculated
    private Viewport viewport;
    private int hashCode = Integer.MIN_VALUE;

    private GeoPrimitive(@Nullable final GeoItem.GeoType type, @NonNull final List<Geopoint> points, @Nullable final GeoIcon icon, final float radius, @Nullable final GeoStyle style, final int zLevel) {
        this.type = type == null || type == GeoType.GROUP ? GeoItem.GeoType.POLYLINE : type;
        this.points = Collections.unmodifiableList(points);
        this.icon = icon;
        this.radius = radius;
        this.style = style;
        this.zLevel = Math.max(-1, zLevel);
    }

    @NonNull
    @Override
    public GeoItem.GeoType getType() {
        return type;
    }

    @NonNull
    public List<Geopoint> getPoints() {
        return points;
    }

    @Nullable
    public GeoIcon getIcon() {
        return icon;
    }

    public float getRadius() {
        return radius;
    }

    @Nullable
    public GeoStyle getStyle() {
        return style;
    }

    public int getZLevel() {
        return zLevel;
    }

    @Nullable
    public Viewport getViewport() {
        if (viewport == null) {
            viewport = Viewport.containing(this.points);
        }
        return viewport;
    }

    @Nullable
    @Override
    public Geopoint getCenter() {
        //shortcut for markers
        if (getPoints().size() == 1) {
            return getPoints().get(0);
        }
        final Viewport vp = getViewport();
        return vp == null ? null : vp.getCenter();
    }

    @Override
    public boolean isValid() {

        if (getIcon() != null && getCenter() == null) {
            return false;
        }

        switch (getType()) {
            case GROUP:
                return false;
            case MARKER:
                return getCenter() != null && getCenter().isValid();
            case CIRCLE:
                return getCenter() != null && getCenter().isValid() && getRadius() > 0;
            case POLYGON:
            case POLYLINE:
            default:
                return getPoints().size() >= 2;
        }
    }

    @Override
    public boolean touches(@NonNull final Geopoint tapped, @Nullable final ToScreenProjector projector) {
        if (!isValid()) {
            return false;
        }

        final float lineWidthDp = GeoStyle.getStrokeWidth(getStyle());
        final boolean isFilled = GeoStyle.getAlpha(GeoStyle.getFillColor(getStyle())) > 0;
        switch (getType()) {
            case POLYLINE:
                if (GeoItemUtils.touchesMultiLine(getPoints(), tapped, lineWidthDp, projector)) {
                    return true;
                }
                break;
            case POLYGON:
                if (GeoItemUtils.touchesPolygon(getPoints(), tapped, lineWidthDp, isFilled, projector)) {
                    return true;
                }
                break;
            case CIRCLE:
                if (GeoItemUtils.touchesCircle(tapped, getCenter(), getRadius(), lineWidthDp, isFilled, projector)) {
                    return true;
                }
                break;
            default:
                break;
        }

        if (getIcon() != null) {
            return getIcon().touchesIcon(tapped, getCenter(), projector);
        }

        return false;
    }

    public Builder buildUpon() {
        return new Builder().setType(type).addPoints(points).setIcon(icon).setRadius(radius).setStyle(style);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static GeoPrimitive createPoint(final Geopoint p, final GeoStyle style) {
        return createCircle(p, 0.1f, style);
    }

    public static GeoPrimitive createMarker(final Geopoint p, final GeoIcon icon) {
        return new Builder().setType(GeoItem.GeoType.MARKER).addPoints(p).setIcon(icon).build();
    }

    public static GeoPrimitive createCircle(final Geopoint p, final float radiusKm, final GeoStyle style) {
        return new Builder().setType(GeoItem.GeoType.CIRCLE).addPoints(p).setRadius(radiusKm).setStyle(style).build();
    }

    public static GeoPrimitive createPolyline(final Collection<Geopoint> p, final GeoStyle style) {
        return new Builder().setType(GeoItem.GeoType.POLYLINE).addPoints(p).setStyle(style).build();
    }

    public static GeoPrimitive createPolygon(final Collection<Geopoint> p, final GeoStyle style) {
        return new Builder().setType(GeoItem.GeoType.POLYGON).addPoints(p).setStyle(style).build();
    }

    //equals/HashCode

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof GeoPrimitive)) {
            return false;
        }
        final GeoPrimitive other = (GeoPrimitive) o;
        return
            Objects.equals(type, other.type) &&
            Objects.equals(points, other.points) &&
            Objects.equals(icon, other.icon) &&
            radius == other.radius &&
            Objects.equals(style, other.style);
    }

    @Override
    public int hashCode() {
        if (hashCode == Integer.MIN_VALUE) {
            hashCode = calculateHashCode();
        }
        return hashCode;
    }

    private int calculateHashCode() {
        return type.ordinal() * 13 + Objects.hashCode(points) ^ (int) radius ^ Objects.hashCode(icon) ^ Objects.hashCode(style);
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(String.valueOf(getType()));
        if (!isValid()) {
            sb.append("!");
        }
        sb.append(":");
        switch (getType()) {
            case POLYGON:
            case POLYLINE:
                sb.append(getPoints().size()).append(" pts [").append(getViewport()).append("]");
                break;
            case CIRCLE:
                sb.append(getCenter()).append(",r:").append(getRadius());
                break;
            default:
                break;
        }
        if (getIcon() != null) {
            sb.append(",icon[").append(getCenter()).append("]:").append(getIcon());
        }
        if (zLevel >= 0) {
            sb.append(",zLevel:").append(zLevel);
        }
        return sb.toString();
    }

    //implements Builder

    public static class Builder {
        private GeoItem.GeoType type;
        private final List<Geopoint> points = new ArrayList<>();
        private GeoIcon icon;
        private float radius;
        private GeoStyle style;

        private int zLevel = -1;

        private Builder() {
            //no free instantiation
        }

        public Builder setType(final GeoItem.GeoType type) {
            this.type = type;
            return this;
        }

        public Builder addPoints(final Collection<Geopoint> gps) {
            points.addAll(gps);
            return this;
        }

        public Builder addPoints(final Geopoint ... gps) {
            points.addAll(Arrays.asList(gps));
            return this;
        }

        public Builder setIcon(final GeoIcon icon) {
            this.icon = icon;
            return this;
        }

        public Builder setRadius(final float radius) {
            this.radius = radius;
            return this;
        }

        public Builder setStyle(final GeoStyle style) {
            this.style = style;
            return this;
        }

        public Builder setZLevel(final int zLevel) {
            this.zLevel = zLevel;
            return this;
        }

        public GeoPrimitive build() {
            return new GeoPrimitive(type, points, icon, radius, style, zLevel);
        }

    }


    // implements Parcelable

    protected GeoPrimitive(final Parcel in) {
        type = GeoItem.GeoType.values()[in.readInt()];
        final List<Geopoint> pointsReadWrite = new ArrayList<>();
        in.readList(pointsReadWrite, Geopoint.class.getClassLoader());
        points = Collections.unmodifiableList(pointsReadWrite);
        icon = in.readParcelable(GeoIcon.class.getClassLoader());
        radius = in.readFloat();
        style = in.readParcelable(GeoStyle.class.getClassLoader());
        zLevel = Math.max(-1, in.readInt());
    }

    public static final Creator<GeoPrimitive> CREATOR = new Creator<GeoPrimitive>() {
        @Override
        public GeoPrimitive createFromParcel(final Parcel in) {
            return new GeoPrimitive(in);
        }

        @Override
        public GeoPrimitive[] newArray(final int size) {
            return new GeoPrimitive[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeInt(type.ordinal());
        dest.writeList(points);
        dest.writeParcelable(icon, flags);
        dest.writeFloat(radius);
        dest.writeParcelable(style, flags);
        dest.writeInt(zLevel);
    }



}
