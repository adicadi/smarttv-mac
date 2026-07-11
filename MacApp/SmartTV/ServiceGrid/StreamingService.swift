import Foundation

/// One streaming service tile in the grid.
struct StreamingService: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var url: URL
    /// SF Symbol name used as the tile icon.
    var icon: String
}
