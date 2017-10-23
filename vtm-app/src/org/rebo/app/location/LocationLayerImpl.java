/*
 * Copyright 2013 Ahmad Saleem
 * Copyright 2013 Hannes Janetzek
 * Copyright 2016 devemux86
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.rebo.app.location;

import org.oscim.layers.LocationLayer;
import org.oscim.map.Map;

class LocationLayerImpl extends LocationLayer {
    private final Compass mCompass;

    LocationLayerImpl(Map map, Compass compass) {
        super(map);
        mCompass = compass;

        locationRenderer.setCallback(compass);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (mCompass != null)
            mCompass.setEnabled(enabled);
    }
}