package thaw.plugins.index;

import java.util.List;

/** List files */
public interface FileList {

	public List<File> getFileList(String columnToSort, boolean asc);
}
