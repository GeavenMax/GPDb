import Foundation

public struct GitHubReleaseInfo: Codable, Identifiable {
    public var id: String { tagName }
    public let tagName: String
    public let name: String
    public let body: String
    public let htmlUrl: String

    enum CodingKeys: String, CodingKey {
        case tagName = "tag_name"
        case name
        case body
        case htmlUrl = "html_url"
    }
}

public actor GitHubUpdateService {
    private let repo = "GeavenMax/GPDb"

    public init() {}

    public func checkLatestRelease() async -> GitHubReleaseInfo? {
        guard let url = URL(string: "https://api.github.com/repos/\(repo)/releases/latest") else { return nil }
        var request = URLRequest(url: url)
        request.setValue("application/vnd.github.v3+json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return nil
            }
            let release = try JSONDecoder().decode(GitHubReleaseInfo.self, from: data)
            return release
        } catch {
            return nil
        }
    }
}
