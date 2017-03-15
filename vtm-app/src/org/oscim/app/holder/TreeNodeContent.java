package org.oscim.app.holder;

import org.oscim.app.R;

public class TreeNodeContent {
    public int icon;
    private String text;
    private String extension = "";
    private String size;
    private String path;
    private String continent;
    private String country;
    private String region;

    public TreeNodeContent(String path) {
        this(!path.contains(".") ? R.string.ic_folder :  R.string.ic_drive_file, path);
    }

    public TreeNodeContent(int icon, String path) {
        this.icon = icon;
        this.path = path.trim();
        int i = path.lastIndexOf("/");
        String fullname = i>=0?path.substring(i+1): this.path;

        boolean isFolder = !fullname.contains(".");

        this.text = isFolder ? fullname : fullname.substring(0,fullname.indexOf("."));
        this.extension = isFolder ? "" : fullname.substring(fullname.indexOf("."));
        parseRegions(this.text);
    }

    public void setExtension(String extension){
        this.extension = extension;
    }

    public String getExtension(){
        return extension;
    }

    public void setText(String text){
        this.text = text;
    }

    public String getText(){
        return text;
    }

    public void setRegion(String region){
        this.region = region;
    }

    public String getRegion(){
        return region;
    }

    public void setCountry(String country){
        this.country = country;
    }

    public String getCountry(){
        return country;
    }

    public void setContinent(String continent){
        this.continent = continent;
    }

    public String getContinent(){
        return continent;
    }

    public void setSize(String size){
        this.size = size;
    }

    public String getSize(){
        return size;
    }

    public String getFullName(){
        return text+extension;
    }

    public String getPath(){
        return this.path.toLowerCase();
    }

    @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (!(o instanceof TreeNodeContent))return false;
        TreeNodeContent i = (TreeNodeContent)o;
        return i.getPath().equals(this.path);
    }

    @Override
    public int hashCode() {
        return (this.path).hashCode();
    }

    public void parseRegions(String name){
        for(Continent continent :Continent.values()){
            if(name.toLowerCase().contains(continent.name().toLowerCase()));
            break;
        }
        String[] arr =name.split("_");
        if(arr.length>1) {
            continent = Capitalize(arr[0]);
            country = Capitalize(arr[1]);
            region = country;
        }
        if(arr.length>2){
            region = "";
            for(int i = 2; i< arr.length; i++){
                region += Capitalize(arr[i]+" ");
            }
        }
    }

    public String Capitalize(String text){
        if(text.length()> 1)
            return String.valueOf(text.charAt(0)).toUpperCase()+text.substring(1);
        return text.toUpperCase();
    }

    enum Continent{
        America,
        Africa,
        Antarctica,
        Europe,
        Asia,
        Australia
    }

}
