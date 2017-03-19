package org.oscim.app.search;

import org.mapsforge.poi.storage.PoiCategory;

import java.util.Collection;

/**
 * Created by gustl on 18.03.17.
 */

public class PoiAreaCategory implements PoiCategory {

    @Override
    public Collection<PoiCategory> deepChildren() {
        return null;
    }

    @Override
    public Collection<PoiCategory> getChildren() {
        return null;
    }

    @Override
    public int getID() {
        return 0;
    }

    @Override
    public PoiCategory getParent() {
        return null;
    }

    @Override
    public String getTitle() {
        return "MapArea";
    }

    @Override
    public void setParent(PoiCategory parent) {

    }
}
