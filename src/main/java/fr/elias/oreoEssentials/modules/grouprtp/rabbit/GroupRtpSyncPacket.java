package fr.elias.oreoEssentials.modules.grouprtp.rabbit;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cross-server packet for Group RTP sessions.
 *
 * <p>When a group RTP portal's {@code rtp.server} is set to a different server,
 * the originating server sends this packet to the target.  The target server
 * will find a single safe location and teleport all listed players once they
 * join (similar to the existing {@code RtpTeleportRequestPacket} pattern).</p>
 *
 * <p>The {@code requestId} deduplicates redeliveries.</p>
 */
public final class GroupRtpSyncPacket extends Packet {

    private String     requestId;
    private String     portalId;
    private String     worldName;
    private List<UUID> playerUuids;

    // RTP search params (sent so target server doesn't need portal config)
    private double rtpCenterX;
    private double rtpCenterZ;
    private double rtpRadius;
    private double rtpMinRadius;
    private int    rtpMinY;
    private int    rtpMaxY;
    private int    rtpAttempts;
    private double clusterRadius;

    public GroupRtpSyncPacket() {}

    public GroupRtpSyncPacket(String requestId, String portalId, String worldName,
                               List<UUID> playerUuids,
                               double rtpCenterX, double rtpCenterZ,
                               double rtpRadius, double rtpMinRadius,
                               int rtpMinY, int rtpMaxY, int rtpAttempts,
                               double clusterRadius) {
        this.requestId     = requestId;
        this.portalId      = portalId;
        this.worldName     = worldName;
        this.playerUuids   = playerUuids;
        this.rtpCenterX    = rtpCenterX;
        this.rtpCenterZ    = rtpCenterZ;
        this.rtpRadius     = rtpRadius;
        this.rtpMinRadius  = rtpMinRadius;
        this.rtpMinY       = rtpMinY;
        this.rtpMaxY       = rtpMaxY;
        this.rtpAttempts   = rtpAttempts;
        this.clusterRadius = clusterRadius;
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeString(requestId);
        out.writeString(portalId);
        out.writeString(worldName);
        out.writeInt(playerUuids.size());
        for (UUID uuid : playerUuids) out.writeUUID(uuid);
        out.writeDouble(rtpCenterX);
        out.writeDouble(rtpCenterZ);
        out.writeDouble(rtpRadius);
        out.writeDouble(rtpMinRadius);
        out.writeInt(rtpMinY);
        out.writeInt(rtpMaxY);
        out.writeInt(rtpAttempts);
        out.writeDouble(clusterRadius);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        requestId     = in.readString();
        portalId      = in.readString();
        worldName     = in.readString();
        int size      = in.readInt();
        playerUuids   = new ArrayList<>(size);
        for (int i = 0; i < size; i++) playerUuids.add(in.readUUID());
        rtpCenterX    = in.readDouble();
        rtpCenterZ    = in.readDouble();
        rtpRadius     = in.readDouble();
        rtpMinRadius  = in.readDouble();
        rtpMinY       = in.readInt();
        rtpMaxY       = in.readInt();
        rtpAttempts   = in.readInt();
        clusterRadius = in.readDouble();
    }

    public String     getRequestId()    { return requestId; }
    public String     getPortalId()     { return portalId; }
    public String     getWorldName()    { return worldName; }
    public List<UUID> getPlayerUuids()  { return playerUuids; }
    public double     getRtpCenterX()   { return rtpCenterX; }
    public double     getRtpCenterZ()   { return rtpCenterZ; }
    public double     getRtpRadius()    { return rtpRadius; }
    public double     getRtpMinRadius() { return rtpMinRadius; }
    public int        getRtpMinY()      { return rtpMinY; }
    public int        getRtpMaxY()      { return rtpMaxY; }
    public int        getRtpAttempts()  { return rtpAttempts; }
    public double     getClusterRadius(){ return clusterRadius; }
}
