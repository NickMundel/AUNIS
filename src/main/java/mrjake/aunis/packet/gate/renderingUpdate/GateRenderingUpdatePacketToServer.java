package mrjake.aunis.packet.gate.renderingUpdate;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import mrjake.aunis.Aunis;
import mrjake.aunis.block.DHDBlock;
import mrjake.aunis.block.StargateBaseBlock;
import mrjake.aunis.item.AunisItems;
import mrjake.aunis.packet.AunisPacketHandler;
import mrjake.aunis.packet.PositionedPacket;
import mrjake.aunis.packet.dhd.renderingUpdate.DHDIncomingWormholePacketToClient;
import mrjake.aunis.packet.gate.renderingUpdate.GateRenderingUpdatePacket.EnumGateAction;
import mrjake.aunis.packet.gate.renderingUpdate.GateRenderingUpdatePacket.EnumPacket;
import mrjake.aunis.stargate.EnumSymbol;
import mrjake.aunis.stargate.StargateNetwork;
import mrjake.aunis.stargate.StargateNetwork.StargatePos;
import mrjake.aunis.stargate.TeleportHelper;
import mrjake.aunis.tileentity.DHDTile;
import mrjake.aunis.tileentity.StargateBaseTile;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class GateRenderingUpdatePacketToServer extends PositionedPacket {
	public GateRenderingUpdatePacketToServer() {}
	
	private int objectID;
	
	public GateRenderingUpdatePacketToServer(int objectID, BlockPos pos) {
		super(pos);
		
		this.objectID = objectID;
	}

	@Override
	public void toBytes(ByteBuf buf) {
		super.toBytes(buf);
		
		buf.writeInt(objectID);
	}
	
	@Override
	public void fromBytes(ByteBuf buf) {
		super.fromBytes(buf);
		
		objectID = buf.readInt();
	}

	
	public static class GateRenderingUpdatePacketToServerHandler implements IMessageHandler<GateRenderingUpdatePacketToServer, IMessage> {		
		@Override
		public IMessage onMessage(GateRenderingUpdatePacketToServer message, MessageContext ctx) {
			ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
			
				EntityPlayerMP player = ctx.getServerHandler().player;
				World world = player.getEntityWorld();
				BlockPos pos = message.pos;
				
				Block block = world.getBlockState(pos).getBlock();
				
				if ( block instanceof StargateBaseBlock || block instanceof DHDBlock ) {
					TargetPoint point = new TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 512);
					
					TileEntity te = world.getTileEntity(pos);
					StargateBaseTile gateTile;
					DHDTile dhdTile;
					
					if ( te instanceof StargateBaseTile ) {
						gateTile = (StargateBaseTile) te;
						dhdTile = gateTile.getLinkedDHD(world);
					}
					
					else if ( te instanceof DHDTile ) {
						dhdTile = (DHDTile) te;
						gateTile = dhdTile.getLinkedGate(world);
					}
					
					else {
						// Bad BlockPos given
						
						return;
					}
					
					if (dhdTile != null && gateTile != null) {						
						EnumSymbol symbol = EnumSymbol.valueOf(message.objectID);
						
						if ( symbol == EnumSymbol.BRB ) {						
							if ( gateTile.isEngaged() ) {
								if ( gateTile.isInitiating() ) {									
									StargatePos targetGate = StargateNetwork.get(world).getStargate( gateTile.dialedAddress );
									World targetWorld = TeleportHelper.getWorld(targetGate.getDimension());
									BlockPos targetPos = targetGate.getPos();
									StargateBaseTile targetTile = (StargateBaseTile) targetWorld.getTileEntity(targetPos);
									
									// clear connection and address, start animation 
									TargetPoint targetPoint = new TargetPoint(targetGate.getDimension(), targetPos.getX(), targetPos.getY(), targetPos.getZ(), 512);
									
									AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.GATE_RENDERER_UPDATE, EnumGateAction.CLOSE_GATE, gateTile), point );
									AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.GATE_RENDERER_UPDATE, EnumGateAction.CLOSE_GATE, targetPos), targetPoint );
									
									targetTile.closeGate(false);
									gateTile.closeGate(false);
								}
								
								else {
									player.sendStatusMessage(new TextComponentString(Aunis.proxy.localize("tile.aunis.dhd_block.incoming_wormhole_warn")), true);
								}
							}
							
							else {
								// Gate is closed, BRB pressed
								
								ItemStack stack = player.getHeldItem(EnumHand.MAIN_HAND);
								if (stack.getItem() == AunisItems.dialerFast) {
									NBTTagCompound compound = stack.getTagCompound();
									
									byte[] symbols = compound.getByteArray("address");
									List<EnumSymbol> dialedAddress = new ArrayList<EnumSymbol>();
									
									for (byte s : symbols)
										dialedAddress.add(EnumSymbol.valueOf(s));
									
									dialedAddress.add(EnumSymbol.ORIGIN);
									
									gateTile.dialedAddress = dialedAddress;	
									// gateTile.setRendererState();
									gateTile.fastDialer = true;
								}
																
//								List<EnumSymbol> gateAddressWithOrigin = gateTile.gateAddress.subList(0, gateTile.gateAddress.size());
								
//								for (EnumSymbol s : gateTile.gateAddress)
//									gateAddressWithOrigin.add(s);
								
//								gateAddressWithOrigin.add(EnumSymbol.ORIGIN);
																
								// Check if symbols entered match the range, last is ORIGIN, target gate exists, and if not dialing self
								if (StargateNetwork.get(world).stargateInWorld(world, gateTile.dialedAddress) && !gateTile.dialedAddress.subList(0, 6).equals(gateTile.gateAddress)) {
									// All check, light it up and start gate animation
									
									StargatePos targetGate = StargateNetwork.get(world).getStargate( gateTile.dialedAddress );
									BlockPos targetPos = targetGate.getPos();
									World targetWorld = TeleportHelper.getWorld(targetGate.getDimension());
									
									StargateBaseTile targetTile = (StargateBaseTile) targetWorld.getTileEntity(targetPos);
									
									AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.DHD_RENDERER_UPDATE, message.objectID, dhdTile), point );
									AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.GATE_RENDERER_UPDATE, EnumGateAction.OPEN_GATE, gateTile), point );
//									dhdTile.setLinkedGateEngagement(true);
//									dhdTile.getDHDRendererState().activeButtons = EnumSymbol.toIntegerList(gateTile.dialedAddress, EnumSymbol.BRB);
									
									gateTile.openGate(true, 0, null);
									
									DHDTile targetDhdTile = targetTile.getLinkedDHD(targetWorld);
									TargetPoint targetPoint = new TargetPoint(targetGate.getDimension(), targetPos.getX(), targetPos.getY(), targetPos.getZ(), 512);
									
									targetTile.openGate(false, gateTile.dialedAddress.size(), gateTile.gateAddress);
									
									// Open target gate
									if (targetDhdTile != null) {
//										targetDhdTile.setLinkedGateEngagement(true);
										
//										List<EnumSymbol> targetDhdSymbols = new ArrayList<>();
//										targetDhdSymbols.addAll(gateTile.gateAddress);
//										for (int i=0; i<gateTile.dialedAddress.size()-1; i++)
//											targetDhdSymbols.add(gateTile.gateAddress.get(i).id);
//										
//										targetDhdSymbols.add(EnumSymbol.ORIGIN);
//										targetDhdSymbols.add(EnumSymbol.BRB);
										
//										targetDhdTile.getDHDRendererState().activeButtons.add(EnumSymbol.BRB.id);
										AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.DHD_RENDERER_UPDATE, message.objectID, targetDhdTile), targetPoint );
									}
									
									AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.GATE_RENDERER_UPDATE, EnumGateAction.OPEN_GATE, targetPos), targetPoint );
								}
								
								else {
									// Address malformed, dialing failed
									// Execute GATE_DIAL_FAILED
//									gateTile.clearLinkedDHDButtons(true);
									gateTile.closeGate(true);
									
									AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.GATE_RENDERER_UPDATE, EnumGateAction.GATE_DIAL_FAILED, gateTile), point );
								}
							}	
							
						}
						
						// Not BRB, some glyph pressed
						else {
							if ( gateTile.addSymbolToAddress(symbol, dhdTile) ) {
								// We can still add glyphs(no limit reached)
								int symbolCount = gateTile.getEnteredSymbolsCount();				
																
								// Update the DHD's renderer
								AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.DHD_RENDERER_UPDATE, message.objectID, dhdTile), point );
								
								// Limit not reached, activating in order
								//if ( gateTile.getMaxSymbols() > symbolCount ) {
								if ( (dhdTile.hasUpgrade() && symbolCount == 8) || (!dhdTile.hasUpgrade() && symbolCount == 7) || (symbolCount == 7 && symbol == EnumSymbol.ORIGIN) ) {
									AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.GATE_RENDERER_UPDATE, EnumGateAction.ACTIVATE_FINAL, gateTile), point );
								}
								
								else {
									AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.GATE_RENDERER_UPDATE, EnumGateAction.ACTIVATE_NEXT, gateTile), point );
								}
								
								StargateNetwork network = StargateNetwork.get(world);
																
								// Light up target gate, if exists
								if ( symbol == EnumSymbol.ORIGIN && network.stargateInWorld(world, gateTile.dialedAddress) ) {	
									
									StargatePos targetGate = StargateNetwork.get(world).getStargate( gateTile.dialedAddress );
									World targetWorld = TeleportHelper.getWorld(targetGate.getDimension());
									
									BlockPos targetPos = targetGate.getPos();
									StargateBaseTile targetTile = (StargateBaseTile) targetWorld.getTileEntity(targetPos);
									DHDTile targetDhdTile = targetTile.getLinkedDHD(targetWorld);
									
									TargetPoint targetPoint = new TargetPoint(targetGate.getDimension(), targetPos.getX(), targetPos.getY(), targetPos.getZ(), 512);
									
									boolean eightChevronDial = gateTile.dialedAddress.size() == 8;
									
									targetTile.incomingWormhole(gateTile.gateAddress, gateTile.dialedAddress.size());
									
									// To renderer: light up chevrons and target dhd glyphs																
									if (targetDhdTile != null) {
//										List<EnumSymbol> incomingAddress = gateTile.gateAddress.subList(0, 6);
//										
//										if (eightChevronDial)
//											incomingAddress.add(gateTile.gateAddress.get(6));
//																				
//										targetDhdTile.getDHDRendererState().activeButtons = EnumSymbol.toIntegerList(incomingAddress, EnumSymbol.ORIGIN);
										
										AunisPacketHandler.INSTANCE.sendToAllAround( new DHDIncomingWormholePacketToClient(targetDhdTile.getPos(), gateTile.gateAddress, eightChevronDial), targetPoint );	
									}
									
//									targetTile.getStargateRendererState().activeChevrons = gateTile.dialedAddress.size() - 1;
//									targetTile.getStargateRendererState().isFinalActive = true;
									
									EnumGateAction gateAction;
									if (eightChevronDial)
										gateAction = EnumGateAction.LIGHT_UP_8_CHEVRONS;
									else
										gateAction = EnumGateAction.LIGHT_UP_7_CHEVRONS;
									
									AunisPacketHandler.INSTANCE.sendToAllAround( new GateRenderingUpdatePacketToClient(EnumPacket.GATE_RENDERER_UPDATE, gateAction, targetPos), targetPoint );
								}
							} // add symbol if
						} // not brb else
					} // gateTile and dhdTile not null if
					
					else {
						// DHD is not linked
						player.sendStatusMessage(new TextComponentString(Aunis.proxy.localize("tile.aunis.dhd_block.not_linked_warn")), true);
					}
					
				} // block loaded if
			}); // runnable

			return null;
		} // IMessage onMessage end
	} // Handler end
}