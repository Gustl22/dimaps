package org.oscim.app.search;

import org.mapsforge.poi.storage.PoiCategory;

import java.util.Collection;

/**
 * Created by gustl on 18.03.17.
 */

public class PoiMapareaCategory implements PoiCategory {

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
        return PoiSearch.CustomPoiCategory.Maparea.name();
    }

    @Override
    public void setParent(PoiCategory parent) {

    }
}
