package org.jasm.item.attribute;

import java.util.List;

import org.jasm.bytebuffer.IByteBuffer;
import org.jasm.bytebuffer.print.IPrintable;

public class TopStackmapVariableinfo extends AbstractStackmapVariableinfo {

	public TopStackmapVariableinfo() {
		super((short)0);
	}

	@Override
	public int getLength() {
		return 1;
	}

	@Override
	public boolean isStructure() {
		return false;
	}

	@Override
	public List<IPrintable> getStructureParts() {
		return null;
	}

	@Override
	public String getPrintLabel() {
		return null;
	}

	@Override
	public String getPrintName() {
		return "top";
	}

	@Override
	public String getPrintArgs() {
		return null;
	}

	@Override
	public String getPrintComment() {
		return null;
	}

	@Override
	protected void doResolve() {
		
	}
	
	@Override
	protected void doVerify() {
		
		
	}

	@Override
	protected void doResolveAfterParse() {
		
	}

	@Override
	protected void doReadBody(IByteBuffer source, long offset) {
		
	}

	@Override
	protected void doWriteBody(IByteBuffer source, long offset) {
		
	}

}
