file=codepoints.txt

while IFS= read -r cmd; do
	x=$(echo $cmd | cut -d" " -f1)
	y="${cmd##* }"
    echo "<string name=\"ic_$x\">&#x$y;</string>" >> material_icon.xml 
done < "$file"
