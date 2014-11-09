package org.jasm.item.attribute;

import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.jasm.JasmConsts;
import org.jasm.bytebuffer.IByteBuffer;
import org.jasm.bytebuffer.print.IPrintable;
import org.jasm.item.AbstractByteCodeItem;
import org.jasm.item.constantpool.AbstractConstantPoolEntry;
import org.jasm.item.constantpool.ConstantPool;
import org.jasm.item.constantpool.IConstantPoolReference;
import org.jasm.item.constantpool.Utf8Info;
import org.jasm.item.descriptor.IllegalDescriptorException;
import org.jasm.item.descriptor.TypeDescriptor;
import org.jasm.item.instructions.AbstractInstruction;
import org.jasm.item.instructions.ILocalVariableReference;
import org.jasm.item.instructions.Instructions;
import org.jasm.item.instructions.LocalVariable;
import org.jasm.item.utils.IdentifierUtils;
import org.jasm.parser.literals.SymbolReference;


public class DebugLocalVariable extends AbstractByteCodeItem implements IConstantPoolReference, ILocalVariableReference {
	
	private int startPC = -1;
	private SymbolReference startInstructionReference;
	private AbstractInstruction startInstruction = null;
	private int length = -1;
	private SymbolReference endInstructionReference;
	private AbstractInstruction endIndsruction = null;
	private SymbolReference nameReference;
	private int nameIndex = -1;
	private Utf8Info name = null;
	private SymbolReference descriptorReference;
	private int descriptorIndex = -1;
	private Utf8Info descriptor = null;
	private SymbolReference variableReference;
	private LocalVariable variable;
	private int index = -1;

	@Override
	public void read(IByteBuffer source, long offset) {
		startPC = source.readUnsignedShort(offset);
		length = source.readUnsignedShort(offset+2);
		nameIndex = source.readUnsignedShort(offset+4);
		descriptorIndex = source.readUnsignedShort(offset+6);
		index = source.readUnsignedShort(offset+8);
	}

	@Override
	public void write(IByteBuffer target, long offset) {
		target.writeUnsignedShort(offset, startInstruction.getOffsetInCode());
		Instructions instr = ((CodeAttributeContent)getParent().getParent().getParent().getParent()).getInstructions();
		int length = 0;
		if (endIndsruction == null) {
			length = instr.getCodeLength()-startInstruction.getOffsetInCode();
		} else {
			length = endIndsruction.getOffsetInCode()-startInstruction.getOffsetInCode();
		}
		target.writeUnsignedShort(offset+2, length);
		target.writeUnsignedShort(offset+4, name.getIndexInPool());
		target.writeUnsignedShort(offset+6, descriptor.getIndexInPool());
		target.writeUnsignedShort(offset+8, index);
	}

	@Override
	public int getLength() {
		return 10;
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
		return "var";
	}
	
	@Override
	public String getTypeLabel() {
		return  getPrintName();
	}

	@Override
	public String getPrintArgs() {
		return getVariableType()+"_"+index+", "+startInstruction.getPrintLabel()+((endIndsruction==null)?"":("->"+endIndsruction.getPrintLabel()))+", "+name.getSymbolName()+", "+descriptor.getSymbolName();
	}

	@Override
	public String getPrintComment() {
		return "name="+name.getValue()+",  descriptor="+descriptor.getValue();
	}

	@Override
	protected void doResolve() {
		name  = (Utf8Info)getConstantPool().get(nameIndex-1);
		descriptor = (Utf8Info)getConstantPool().get(descriptorIndex-1);
		Instructions instr = ((CodeAttributeContent)getParent().getParent().getParent().getParent()).getInstructions();
		startInstruction = instr.getInstructionAtOffset(startPC);
		if (startPC+length == instr.getCodeLength()) {
			
		} else {
			endIndsruction = instr.getInstructionAtOffset(startPC+length);
		}

	}
	
	@Override
	protected void doResolveAfterParse() {
		Instructions instr = ((CodeAttributeContent)getParent().getParent().getParent().getParent()).getInstructions();
		ConstantPool cp = getConstantPool();
		
		startInstruction = instr.checkAndLoadFromSymbolTable(this, startInstructionReference);
		if (endInstructionReference != null) {
			endIndsruction = instr.checkAndLoadFromSymbolTable(this, endInstructionReference);
		}
		
		name = cp.checkAndLoadFromSymbolTable(this, Utf8Info.class, nameReference);
		descriptor = cp.checkAndLoadFromSymbolTable(this, Utf8Info.class, descriptorReference);
		
		
		if (name != null) {
			IdentifierUtils.checkIdentifier(this, nameReference, name);
		}
		
		if (descriptor != null) {
			try {
				TypeDescriptor d = new TypeDescriptor(descriptor.getValue());
				variable = instr.getVariablesPool().checkAndLoad(this, variableReference, getVariableType());
				index = variable.getIndex();
			} catch (IllegalDescriptorException e) {
				emitError(descriptorReference, "malformed type descriptor "+descriptor.getValue());
			}
		}
		
	}

	@Override
	public AbstractConstantPoolEntry[] getConstantReferences() {
		return new AbstractConstantPoolEntry[]{name,descriptor};
	}
	
	protected char getVariableType() throws IllegalDescriptorException {
		TypeDescriptor desc = new TypeDescriptor(descriptor.getValue());
		if (desc.isBoolean() || 
			desc.isByte() || 
			desc.isCharacter() ||
			desc.isInteger() ||
			desc.isShort()) {
			return JasmConsts.LOCAL_VARIABLE_TYPE_INT;
		} else if (desc.isDouble()) {
			return JasmConsts.LOCAL_VARIABLE_TYPE_DOUBLE;
		} else if (desc.isFloat()) {
			return JasmConsts.LOCAL_VARIABLE_TYPE_FLOAT;
		} else if (desc.isLong()) {
			return JasmConsts.LOCAL_VARIABLE_TYPE_LONG;
		} else if (desc.isObject() || desc.isArray()) {
			return JasmConsts.LOCAL_VARIABLE_TYPE_REFERENCE;
		} else {
			throw new IllegalStateException("Clouldn't convert: "+desc);
		}
			
		
	}

	@Override
	public org.jasm.item.instructions.LocalVariable[] getLocalVariableReferences() {
		return new org.jasm.item.instructions.LocalVariable[]{new org.jasm.item.instructions.LocalVariable(index,getVariableType())};
	}

	public void setStartInstructionReference(
			SymbolReference startInstructionReference) {
		this.startInstructionReference = startInstructionReference;
	}

	public void setEndInstructionReference(SymbolReference endInstructionReference) {
		this.endInstructionReference = endInstructionReference;
	}

	public void setNameReference(SymbolReference nameReference) {
		this.nameReference = nameReference;
	}

	public void setDescriptorReference(SymbolReference descriptorReference) {
		this.descriptorReference = descriptorReference;
	}

	public void setVariableReference(SymbolReference variableReference) {
		this.variableReference = variableReference;
	}

	public AbstractInstruction getStartInstruction() {
		return startInstruction;
	}

	public AbstractInstruction getEndIndsruction() {
		return endIndsruction;
	}

	public Utf8Info getName() {
		return name;
	}

	public Utf8Info getDescriptor() {
		return descriptor;
	}

	public LocalVariable getVariable() {
		return variable;
	}
	
	
	
	

}
